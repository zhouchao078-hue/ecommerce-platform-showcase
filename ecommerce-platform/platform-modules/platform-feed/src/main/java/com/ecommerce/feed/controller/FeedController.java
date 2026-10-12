package com.ecommerce.feed.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.auth.service.AuthService;
import com.ecommerce.auth.model.SysUser;
import com.ecommerce.common.result.Result;
import com.ecommerce.feed.model.FeedPost;
import com.ecommerce.feed.service.FeedService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;
    private final AuthService authService;

    /**
     * 发布动态
     */
    @PostMapping
    public Result<Void> publish(@RequestBody Map<String, String> body) {
        Long userId = authService.getCurrentUserId();
        SysUser user = authService.getUserById(userId);
        feedService.publish(userId, user.getUsername(), body.getOrDefault("content", ""));
        return Result.success();
    }

    /**
     * 广场动态列表（分页，按时间倒序）
     */
    @GetMapping
    public Result<IPage<FeedPost>> listPosts(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.success(feedService.listPosts(pageNum, pageSize));
    }
}
