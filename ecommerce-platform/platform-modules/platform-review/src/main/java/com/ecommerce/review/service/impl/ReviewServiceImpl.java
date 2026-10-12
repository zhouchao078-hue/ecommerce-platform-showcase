package com.ecommerce.review.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.mapper.OrderItemMapper;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.review.mapper.ReviewMapper;
import com.ecommerce.review.model.Review;
import com.ecommerce.review.service.ReviewService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {
    private final ReviewMapper reviewMapper;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;


    @Override
   @Transactional(rollbackFor = Exception.class)
   public void addReview(long userId, long productId, long orderId, Integer rating, String content){
        if( rating == null || !(rating <= 5 && rating >= 1 ) ){
            throw new BizException(ErrorCode.BAD_REQUEST,"rating必须在合法范围内");
        }
        if( content == null || content.isBlank() || content.length() >= 500){
            throw new BizException(ErrorCode.BAD_REQUEST,"内容不在有效范围内");
        }
       if(orderId <= 0){
           throw new BizException(ErrorCode.BAD_REQUEST,"订单号无效");
       }
       Order order = orderMapper.selectById(orderId);
       if(order == null){
            throw new BizException(ErrorCode.BAD_REQUEST,"此订单不存在");
       }
       if(!order.getUserId().equals(userId)){
           throw new BizException(ErrorCode.BAD_REQUEST,"无权评价");
       }
       if(!order.getStatus().equals(OrderStatus.COMPLETED.getCode())){
           throw new BizException(ErrorCode.BAD_REQUEST,"只有订单完成后才能评价");
       }
      LambdaQueryWrapper <OrderItem> iw = new LambdaQueryWrapper<>();
       iw.eq(OrderItem::getOrderId, orderId).eq(OrderItem::getProductId, productId);
       if (orderItemMapper.selectCount(iw) == 0) {
           throw new BizException(ErrorCode.BAD_REQUEST, "未购买该商品，无法评价");
       }
       Review review = new Review();
       review.setUserId(userId);
       review.setProductId(productId);
       review.setOrderId(orderId);
       review.setRating(rating);
       review.setContent(content);
       review.setCreateTime(LocalDateTime.now());
       reviewMapper.insert(review);
   }

    @Override
    public IPage<Review> listReview(Long productId, Integer pageNum, Integer pageSize) {
        if(productId <= 0){
            throw new BizException(ErrorCode.BAD_REQUEST,"此商品没有被评论过");
        }
       LambdaQueryWrapper <Review> queryWrapper = new LambdaQueryWrapper<>();
       queryWrapper.eq(Review::getProductId, productId)
               .orderByDesc(Review::getCreateTime);
        return reviewMapper.selectPage(new Page<>(pageNum, pageSize), queryWrapper);
    }

    @Override
    public IPage<Review> listReviewByUserId(long userId, Integer pageNum, Integer pageSize) {
       if(userId <= 0){
           throw new BizException(ErrorCode.BAD_REQUEST,"用户没有评论过");
       }
       LambdaQueryWrapper <Review> queryWrapper = new LambdaQueryWrapper<>();
       queryWrapper.eq(Review::getUserId, userId)
               .orderByDesc(Review::getCreateTime);
        return reviewMapper.selectPage(new Page<>(pageNum, pageSize), queryWrapper);
    }


}
