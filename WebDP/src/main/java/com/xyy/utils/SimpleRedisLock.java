package com.xyy.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name; // 指定业务的名称，不然所有业务在用的同一把分布式锁，显然不合理

    @Resource
    private StringRedisTemplate redisTemplate;

    public SimpleRedisLock(String name) {
        this.name = name;
    }

    private static final String LOCK_KEY_PREFIX = "lock";
    private static final String LOCK_VALUE_PREFIX = UUID.randomUUID().toString();
    @Override
    public boolean tryLock(long timeoutSec) {
        String threadName = Thread.currentThread().getName();
        Boolean ans = redisTemplate.opsForValue().setIfAbsent(LOCK_KEY_PREFIX + name, LOCK_VALUE_PREFIX+threadName, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ans);
    }

    @Override
    public void unLock() {
        String value = LOCK_VALUE_PREFIX + Thread.currentThread().getName();
        String s = redisTemplate.opsForValue().get(LOCK_KEY_PREFIX + name);
        // 判断释放的锁和当前加的锁是否是同一把
        if(value.equals(s)){
            redisTemplate.delete(LOCK_KEY_PREFIX + name);
        }
    }
}
