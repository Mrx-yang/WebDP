package com.xyy.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.xyy.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {

    // 因为LoginInterceptor这个类使我们自己手动创建的，不是spring创建的(加上@Configuration注解的就是spring创建的)，所以这里的stringRedisTemplate不能使用autoWired进行注入
    // 只能通过构造函数为其赋值，然后到MvcConfig中修改代码，通过构造函数为stringRedisTemplate赋值
    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    // 其实这里的刷新Redis中token的有效期还不是那么严谨，因为当前的拦截器只是拦截指定的需要登录的页面，也就是说只有用户重新访问这些需要登录的页面，才会刷新token
    // 但其实如果访问那些不需要登录的页面是，如果当前用户已经登录，也应该刷新token的有效期，具体的优化改进参考P34，这里就不写了
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        /*
        // 1 获取session
        HttpSession session = request.getSession();
        Object user = session.getAttribute("user");
        if(user == null){
            response.setStatus(401); // 不存在该用户，直接拦截，返回401
            return false;
        }
        // 保存用户信息到ThreadLocal中
        UserHolder.saveUser((UserDTO) user);
        return true;
         */

        // 现在不用session了，改为Redis来实现
        // 先获取前端携带的token信息，然后根据token信息在Redis中找到对应的用户
        String token = request.getHeader("authorization"); // 前端代码中token是放在"authorization"这个里面的
        if(StrUtil.isBlank(token)){
            response.setStatus(401); // 不存在该用户，直接拦截，返回401
            return false;
        }

        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
        if(userMap.isEmpty()){
            response.setStatus(401); // 不存在该用户，直接拦截，返回401
            return false;
        }

        // 然后把userMap转为对象类型
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 把用户信息放到ThreadLocal中
        UserHolder.saveUser(userDTO);

        // 刷新token在Redis中的有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        return true;

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();
    }
}
