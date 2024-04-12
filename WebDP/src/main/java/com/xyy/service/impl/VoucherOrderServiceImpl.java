package com.xyy.service.impl;

import com.xyy.dto.Result;
import com.xyy.entity.SeckillVoucher;
import com.xyy.entity.VoucherOrder;
import com.xyy.mapper.VoucherOrderMapper;
import com.xyy.service.ISeckillVoucherService;
import com.xyy.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xyy.utils.RedisIDWorker;
import com.xyy.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {


    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIDWorker redisIDWorker;

    @Resource
    private RedissonClient redissonClient;


    private static final DefaultRedisScript<Long> SECKILL_SCRIPT; // 执行lua脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024*1024);

    // 异步线程把订单信息写入数据库
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            while(true){ 
                try {
                    // 获取阻塞队列中的信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 处理订单，写入数据库
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) { // 这里太复杂了就不想写了

    }

    @Override
    public Result secKillVoucher(Long voucherId) { // 异步秒杀逻辑
        // 对于秒杀时间的判断这里就忽略了
        // 1.执行LUA脚本
        Long userId = UserHolder.getUser().getId();
        Long ans = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        if(ans.intValue() != 0)
            Result.fail(ans.intValue() == 1 ? "库存不足" : "不能重复下单");

        // 如果返回0，有购买资格，生成订单Id
        Long orderId = redisIDWorker.nextId("order");

        // 然后把订单信息写入阻塞队列BlockingQueue中
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        orderTasks.add(voucherOrder);


        return Result.ok(orderId);
    }

    @Override
    public Result secKillVoucher1(Long voucherId) {
        // 查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
       if(voucher.getBeginTime().isAfter(LocalDateTime.now()))
           return Result.fail("秒杀还未开始");
       if(voucher.getEndTime().isBefore(LocalDateTime.now()))
           return Result.fail("秒杀已经结束");

       if(voucher.getStock() < 1)
           return Result.fail("库存不足");


       Long userId = UserHolder.getUser().getId();
       /**
       synchronized (userId.toString().intern()){ // 应该锁userId的值，而不是每个userId对象，所以要用intern函数获取userId在字符串常量池中的对应对象，这样就相当于锁的是值了
           // 注意点：createVoucherOrder是事务方法，而事务底层是由aop使用事务代理来实现的，所以这里如果直接调用相当于默认用的this去调用，是错误的
           // return createVoucherOrder(voucherId); 不能直接返回，要要调用aop去实现
           IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy(); // 获取事务的代理执行对象
           return proxy.createVoucherOrder(voucherId);
       }
        */

        // 上面锁的方式在多集群的情况会出现线程安全，这里改为Redis实现的分布式锁，传递业务名称，但是同时还要要锁用户id！
        // 这里就实现了集群环境下对用户id的分布式锁
        // SimpleRedisLock simpleRedisLock = new SimpleRedisLock("killSecOrder" + userId);

        // 但是上述方法依旧存在问题，这里直接采用成熟的Redisson生成的锁(可重入锁)
        RLock lock = redissonClient.getLock("killSecOrder" + userId);

        boolean success = lock.tryLock();
        if(!success)
            return Result.fail("不允许重复下单");

        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy(); // 获取事务的代理执行对象
            return proxy.createVoucherOrder(voucherId);
        }finally {
            lock.unlock(); // 一定要释放锁
        }
    }

    @Transactional // 增加事务,确保创建订单的操作是原子性操作
    public Result createVoucherOrder(Long voucherId) {
        // 实现一人一单，但是需要加锁，不加锁会存在线程安全问题
        long userId = UserHolder.getUser().getId();
        // 从数据库中查询用户是否已经对该优惠券下单过一次了，
        int cnt = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (cnt > 0)
            return Result.fail("一人只能下单一次！");

        boolean success = seckillVoucherService.update().
                setSql("stock = stock-1").
                eq("voucher_id", voucherId).
                gt("stock", 0). // 乐观锁情况下，只要保证库存大于0就可以减
                update();

        // eq("stock", voucher.getStock()). // 这种加锁方式会导致失败率较高
        // update();


        if(!success)
            return Result.fail("库存不足");


        // 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 这里只需要管订单id(用前面写的全局唯一id)，用户id，代金券id
        long orderId = redisIDWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 用户ID可以通过UserHolder获得,UserHolder里面就是用ThreadLocal实现的
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);


        // 把订单写入数据库
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
