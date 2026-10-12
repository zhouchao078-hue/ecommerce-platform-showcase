package com.ecommerce.user.service;

import com.ecommerce.user.model.Notification;

import java.util.List;

public interface NotificationService {

    /**
     * 发一条通知。
     *
     * 用独立事务（REQUIRES_NEW）+ 吞异常：通知是副作用，
     * 它的失败绝不能让主流程回滚（退款已经成功了，不能因为通知写失败就当没退）。
     */
    void send(Long userId, int type, String title, String content,
              int refType, Long refId, String dedupKey);

    /**
     * 批量发送同一事件的通知。
     * 使用 INSERT IGNORE + 唯一索引去重，避免同一批出现重复数据。
     */
    void sendBatch(List<Long> userIds, int type, String title, String content,
                   int refType, Long refId, String dedupKey);

    List<Notification> list(Long userId, int limit);

    long unreadCount(Long userId);

    void markRead(Long userId, Long id);

    void markAllRead(Long userId);
}
