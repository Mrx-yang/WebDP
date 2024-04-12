package com.xyy.service;

import com.xyy.dto.Result;
import com.xyy.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result secKillVoucher(Long voucherId);

    Result secKillVoucher1(Long voucherId);

    Result createVoucherOrder(Long voucherId);
}
