package com.xyy;

import com.xyy.entity.Voucher;
import com.xyy.service.impl.VoucherServiceImpl;
import com.xyy.utils.RedisIDWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class WebDPApplicationTests {


    @Resource // 因为我们给RedisIDWorker这个类添加了@Component注解，所以这里可以使用@Resource进行注入
    private RedisIDWorker redisIDWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    public void testRedisIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++){
                long id = redisIDWorker.nextId("miaoshao Order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end-begin));

    }

    @Resource
    VoucherServiceImpl voucherService;
    @Test
    public void addSecVoucher(){
        Voucher voucher = new Voucher();
        voucher.setShopId(2L);
        voucher.setTitle("100元代金券");
        voucher.setSubTitle("周一到周五都可使用");
        voucher.setRules("全场通用");
        voucher.setPayValue(8000L);
        voucher.setActualValue(10000L);
        voucher.setType(1);
        voucher.setStock(100);
        voucher.setBeginTime(LocalDateTime.parse("2024-02-29T21:58:17"));
        voucher.setEndTime(LocalDateTime.parse("2024-03-11T21:58:17"));

        voucherService.addSeckillVoucher(voucher);

    }
}
