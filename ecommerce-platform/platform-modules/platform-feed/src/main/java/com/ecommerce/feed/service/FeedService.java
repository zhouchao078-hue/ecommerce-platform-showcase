package com.ecommerce.feed.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.feed.model.FeedPost;

public interface FeedService {

    /**
     * 发布一条动态
     *
     * @param userId   发布用户ID
     * @param username 发布用户名
     * @param content  动态内容
     */
    void publish(Long userId, String username, String content);

    /**
     * 分页查询广场动态（按时间倒序）
     *
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<FeedPost> listPosts(Integer pageNum, Integer pageSize);
}
