package com.xyy.utils;

public interface ILock {

    /**
     *  尝试获取锁
     *  timeoutSec 锁持有的超时时间，过期后自动释放，利用Redis的setNx指定锁的超时时间
     *  获取成功返回true，失败直接返回false
     */
    boolean tryLock(long timeoutSec);


    void unLock(); // 释放锁
}
