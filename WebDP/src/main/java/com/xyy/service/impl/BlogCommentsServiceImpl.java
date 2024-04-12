package com.xyy.service.impl;

import com.xyy.entity.BlogComments;
import com.xyy.mapper.BlogCommentsMapper;
import com.xyy.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
