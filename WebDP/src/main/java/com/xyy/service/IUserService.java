package com.xyy.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xyy.dto.LoginFormDTO;
import com.xyy.dto.Result;
import com.xyy.entity.User;

import javax.servlet.http.HttpSession;


public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result sendCodeWithRedis(String phone, HttpSession session); // 改用redis来存储验证码信息，解决session共享问题

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result loginWithRedis(LoginFormDTO loginForm, HttpSession session);
}
