package com.xyy.service;

import com.xyy.dto.Result;
import com.xyy.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IShopService extends IService<Shop> {

    Result queryById(Long id);

    Result updateShopById(Shop shop);
}
