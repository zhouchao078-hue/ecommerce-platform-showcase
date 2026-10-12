package com.ecommerce.user.controller;

import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.user.model.Notification;
import com.ecommerce.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 站内通知 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthService authService;

    @GetMapping
    public Result<List<Notification>> list(@RequestParam(defaultValue = "50") Integer limit) {
        return Result.success(notificationService.list(authService.getCurrentUserId(), limit));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        long n = notificationService.unreadCount(authService.getCurrentUserId());
        return Result.success(Map.of("count", n));
    }

    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        notificationService.markRead(authService.getCurrentUserId(), id);
        return Result.success();
    }

    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        notificationService.markAllRead(authService.getCurrentUserId());
        return Result.success();
    }
}
