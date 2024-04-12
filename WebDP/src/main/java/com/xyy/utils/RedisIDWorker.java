package com.xyy.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker { // 利用Redis生成全局唯一ID
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public long nextId(String keyPrefix){
        // 生成时间戳
        Instant instant = Instant.now(); // 当前时间
        long timestamp = instant.toEpochMilli();

        // 利用redis自增功能生成某个key的序列号，key用业务前缀+日期组成

        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long num = stringRedisTemplate.opsForValue().increment(keyPrefix + ":" + date);

        // 拼接时间戳和date得到最后的全局唯一ID
        return (timestamp << 32) | num;
    }


}
