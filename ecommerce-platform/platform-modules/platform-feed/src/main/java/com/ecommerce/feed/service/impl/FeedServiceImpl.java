package com.ecommerce.feed.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.feed.mapper.FeedPostMapper;
import com.ecommerce.feed.model.FeedPost;
import com.ecommerce.feed.service.FeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedServiceImpl implements FeedService {

    private final FeedPostMapper feedPostMapper;

    @Override
    public void publish(Long userId, String username, String content) {
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "动态内容不能为空");
        }
        if (content.length() > 500) {
            throw new BizException(ErrorCode.BAD_REQUEST, "动态内容不能超过500字");
        }
        FeedPost post = new FeedPost();
        post.setUserId(userId);
        post.setUsername(username);
        post.setContent(content.trim());
        post.setCreateTime(LocalDateTime.now());
        feedPostMapper.insert(post);
        log.info("发布动态成功: userId={}, postId={}", userId, post.getId());
    }

    @Override
    public IPage<FeedPost> listPosts(Integer pageNum, Integer pageSize) {
        QueryWrapper<FeedPost> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        return feedPostMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }
}
