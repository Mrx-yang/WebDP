package com.xyy.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.xyy.dto.Result;
import com.xyy.entity.Blog;
import com.xyy.entity.User;
import com.xyy.mapper.BlogMapper;
import com.xyy.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xyy.service.IUserService;
import com.xyy.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    IUserService userService;
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null)
            return Result.fail("blog不存在");

        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
        // 查询blog是否已经被点赞
        String key = "blog:liked:" + id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(isMember));

        return Result.ok(blog);
    }

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result likeBlog(Long id) {
        // 判断当前登录的用户是否已经点赞
        Long userId = UserHolder.getUser().getId();
        String key = "blog:liked:" + id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if(BooleanUtil.isFalse(isMember)){
            update().setSql("liked = liked+1").eq("id", id);
            stringRedisTemplate.opsForSet().add(key, userId.toString());
        }else{
            update().setSql("liked = liked-1").eq("id", id);
            stringRedisTemplate.opsForSet().remove(key, userId.toString());
        }
        return Result.ok();
    }
}
