package com.xyy.service.impl;

import com.xyy.entity.UserInfo;
import com.xyy.mapper.UserInfoMapper;
import com.xyy.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
