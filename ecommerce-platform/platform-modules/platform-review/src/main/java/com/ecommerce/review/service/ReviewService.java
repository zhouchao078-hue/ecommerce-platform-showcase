package com.ecommerce.review.service;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.review.model.Review;

import java.util.List;

public interface ReviewService {
    /**
     * 添加评论
     * @param userId
     * @param productId
     * @param orderId
     * @param rating
     * @param content
     */
    void addReview(long userId, long productId, long orderId, Integer rating, String content);
    /**
     *查询评论
     */
    IPage<Review> listReview(Long productId, Integer pageNum, Integer pageSize);

    /**
     * 查询自己的评论
     */
    IPage<Review> listReviewByUserId(long userId,Integer pageNum, Integer pageSize);
}
