package com.ecommerce.review.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.api.review.dto.Reviewdto;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.review.model.Review;
import com.ecommerce.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final AuthService authService;
    private final ReviewService reviewService;

    @PostMapping("/reviews")
    public Result<Void> createReview(@RequestBody Reviewdto dto){
        long userId = authService.getCurrentUserId();
        reviewService.addReview(userId,dto.getProductId(),dto.getOrderId(),dto.getRating(),dto.getContent());
        return Result.success();
    }
    @GetMapping("/reviews/product/{productId}")
    public Result<IPage<Review>> listByReviews(@PathVariable("productId") long productId,
                                                @RequestParam(defaultValue = "1") Integer pageNum,
                                              @RequestParam(defaultValue = "10") Integer pageSize){
        IPage<Review> reviews = reviewService.listReview(productId, pageNum, pageSize);
        return Result.success(reviews);
    }
    @GetMapping("/reviews/mine")
    public Result<IPage<Review>> listByMine(@RequestParam(defaultValue = "1") Integer pageNum,
                                            @RequestParam(defaultValue = "10") Integer pageSize){
        long userId = authService.getCurrentUserId();
        IPage<Review> reviews = reviewService.listReviewByUserId(userId, pageNum, pageSize);
        return Result.success(reviews);

    }
}
