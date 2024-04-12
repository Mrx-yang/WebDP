package com.xyy.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.xyy.dto.Result;
import com.xyy.entity.Shop;
import com.xyy.mapper.ShopMapper;
import com.xyy.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.xyy.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.xyy.utils.RedisConstants.CACHE_SHOP_TTL;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {  // 添加一层Redis缓存加快查询的速度
        // 首先从缓存中进行查询
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 如果缓存中查不到就需要查询数据库
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        if(shopJson == "")
            return Result.fail("不存在这样的店铺"); // 处理缓存穿透现象

        Shop shop = getById(id);
        // 如果数据库中也查不到，就返回错误信息
        if(shop == null){
            stringRedisTemplate.opsForValue().set(key, "", 2L, TimeUnit.MINUTES); // 缓存空值防止缓存穿透
            return Result.fail("没有与id为" + id + "对应的店铺信息");
        }

        // 如果数据库中能查到，就把查到的结果写入缓存，并返回查询到的值
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
    }


    @Override // 先更新数据库，再更新缓存
    public Result updateShopById(Shop shop) {
        Long id = shop.getId();
        if(id == null)
            return Result.fail("不存在该店铺对应的id");

        // 先更新数据库再删除缓存
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();

    }
}
