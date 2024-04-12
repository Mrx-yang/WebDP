package com.xyy.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xyy.dto.LoginFormDTO;
import com.xyy.dto.Result;
import com.xyy.dto.UserDTO;
import com.xyy.entity.User;
import com.xyy.mapper.UserMapper;
import com.xyy.service.IUserService;
import com.xyy.utils.RegexUtils;
import com.xyy.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.xyy.utils.RedisConstants.*;



// 这个类继承了ServiceImpl<UserMapper, User>，这是mybatis-plus中的对象，因此在此类中可以直接调用UserMapper去操作User表
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) { // 发送验证码
         // 1. 校验手机号是否是符合规范的手机号
        if(RegexUtils.isPhoneInvalid(phone))
            return Result.fail("不合法的手机号");

        // 2. 如果符合就生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4. 保存验证码到session中
        session.setAttribute(phone, code); // 我觉得应该这样做才合理
        // session.setAttribute("code", code);

        // 5. 发送验证码。这里需要用到实际的服务器，忽略
        log.debug("验证码发送成功，验证码是：" + code);

        // 返回OK
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) { // 用户登录
        // 1. 校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("不合法的手机号");
        }

        // 2. 校验验证码
        Object cacheCode = session.getAttribute(phone);
        String code = loginForm.getCode();
        if(cacheCode == null)
            return Result.fail("验证码失效，请重试");
        if(!cacheCode.toString().equals(code))
            return Result.fail("验证码错误");

        // 3. 判断用户是否存在，如果不存在就创建
        User user = query().eq("phone", phone).one(); // 去tb_user表中查询“phone”字段中所有等于phone的对象
        if(user == null){
            user = createUserWithPhone(phone);
        }

        // 4. 保存用户对象到session中,key为手机号，value为用户对象，但是只应该存储登录信息即可，一方面可以隐藏敏感信息，另一方面减少存储压力
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class)); // 转化类型

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result sendCodeWithRedis(String phone, HttpSession session) { // 使用Redis来验证登录，解决session共享问题
        if(RegexUtils.isPhoneInvalid(phone))
            return Result.fail("不合法的手机号");

        String code = RandomUtil.randomNumbers(6);

        // 把code存入Redis，key为手机号，并且加上当前业务的前缀（静态常量表示），并且指定存活时间为一分钟
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.debug("验证码发送成功，验证码是：" + code);
        return Result.ok();
    }

    @Override
    public Result loginWithRedis(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("不合法的手机号");
        }

        // 2. 校验验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if(cacheCode == null)
            return Result.fail("验证码失效，请重试");
        if(!cacheCode.equals(code))
            return Result.fail("验证码错误");

        // 3. 判断用户是否存在，如果不存在就创建
        User user = query().eq("phone", phone).one(); // 去tb_user表中查询“phone”字段中所有等于phone的对象
        if(user == null){
            user = createUserWithPhone(phone);
        }

        // UUID生成Token作为Redis中存储用户对象的key
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        // 把userDTO转为hashmap然后放入Redis中进行存储
        // 下面这样写会报错class java.lang.Long cannot be cast to class java.lang.String，因为userDTO里面的long类型不能转为String
        // stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token, BeanUtil.beanToMap(userDTO));
        // 手动定义把long类型的字段值转为string
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,
                BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().setFieldValueEditor((filedName, fieldValue) -> fieldValue.toString()))
        );

        // 同理也需要设置一个有效期，不然用户信息太多Redis存不下，只不过有效期可以长一些，参考session的有效期1h
        // 但是如果中途被访问过，需要刷新有效时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token, LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 注意这里需要返回token
        return Result.ok(token);
    }

}
