package com.ecommerce.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.ecommerce.user.mapper.NotificationMapper;
import com.ecommerce.user.model.Notification;
import com.ecommerce.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int BATCH_SIZE = 500;

    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void send(Long userId, int type, String title, String content,
                     int refType, Long refId, String dedupKey) {
        if (userId == null) return;
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setTitle(title);
            n.setContent(content);
            n.setRefType(refType);
            n.setRefId(refId);
            n.setDedupKey(dedupKey == null ? "" : dedupKey);
            n.setIsRead(0);
            n.setCreateTime(LocalDateTime.now());
            notificationMapper.insert(n);
        } catch (DuplicateKeyException e) {
            // 唯一键拦下的重复通知，属于正常情况（比如同一商品同一天第二次降价），静默忽略
            log.debug("通知已存在，跳过: userId={}, type={}, refId={}", userId, type, refId);
        } catch (Exception e) {
            // 兜底：通知失败不影响主流程，只记日志
            log.warn("写通知失败（不影响主流程）: userId={}, type={}, refId={}, err={}",
                    userId, type, refId, e.getMessage());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void sendBatch(List<Long> userIds, int type, String title, String content,
                          int refType, Long refId, String dedupKey) {
        if (userIds == null || userIds.isEmpty()) return;
        List<Long> distinctUserIds = new ArrayList<>(userIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());
        if (distinctUserIds.isEmpty()) return;
        try {
            int inserted = 0;
            for (int from = 0; from < distinctUserIds.size(); from += BATCH_SIZE) {
                int to = Math.min(from + BATCH_SIZE, distinctUserIds.size());
                inserted += notificationMapper.insertIgnoreBatch(
                        distinctUserIds.subList(from, to), type, title, content, refType, refId,
                        dedupKey == null ? "" : dedupKey);
            }
            log.info("批量通知发送完成: requested={}, inserted={}, type={}, refId={}",
                    distinctUserIds.size(), inserted, type, refId);
        } catch (Exception e) {
            log.warn("批量通知发送失败（不影响主流程）: type={}, refId={}, err={}",
                    type, refId, e.getMessage());
        }
    }

    @Override
    public List<Notification> list(Long userId, int limit) {
        QueryWrapper<Notification> w = new QueryWrapper<>();
        w.eq("user_id", userId).orderByDesc("id").last("limit " + Math.max(1, Math.min(limit, 200)));
        return notificationMapper.selectList(w);
    }

    @Override
    public long unreadCount(Long userId) {
        QueryWrapper<Notification> w = new QueryWrapper<>();
        w.eq("user_id", userId).eq("is_read", 0);
        return notificationMapper.selectCount(w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long id) {
        UpdateWrapper<Notification> w = new UpdateWrapper<>();
        // 带上 user_id 是防止越权把别人的通知标记成已读
        w.eq("id", id).eq("user_id", userId).set("is_read", 1);
        notificationMapper.update(null, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllRead(Long userId) {
        UpdateWrapper<Notification> w = new UpdateWrapper<>();
        w.eq("user_id", userId).eq("is_read", 0).set("is_read", 1);
        notificationMapper.update(null, w);
    }
}
