package com.ecommerce.app.notification;

import com.ecommerce.merchant.model.UdFollow;
import com.ecommerce.merchant.service.FollowService;
import com.ecommerce.product.event.ProductPriceDropEvent;
import com.ecommerce.user.model.Notification;
import com.ecommerce.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 在应用层连接商品、关注和通知三个模块。
 *
 * 等通知量大到需要异步重放、补发时，只需把这里的发送动作替换为发送 MQ 消息，
 * 商品改价逻辑本身不需要改动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductPriceDropNotificationListener {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final FollowService followService;
    private final NotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductPriceDrop(ProductPriceDropEvent event) {
        try {
            List<Long> followerIds = followService.listFollowerIds(
                    UdFollow.TYPE_PRODUCT, event.productId());
            if (followerIds.isEmpty()) return;

            String productName = event.productName() == null ? "商品" : event.productName();
            String content = "你关注的「" + productName + "」已从 ¥"
                    + formatPrice(event.oldPrice()) + " 降至 ¥" + formatPrice(event.newPrice());
            String dedupKey = LocalDate.now(BUSINESS_ZONE)
                    .format(DateTimeFormatter.BASIC_ISO_DATE);

            notificationService.sendBatch(
                    followerIds,
                    Notification.TYPE_PRICE_DROP,
                    "关注商品降价",
                    content,
                    Notification.REF_PRODUCT,
                    event.productId(),
                    dedupKey);
        } catch (Exception e) {
            // 商品改价事务已经提交，通知失败不能反向影响改价结果
            log.warn("处理商品降价通知失败: productId={}, err={}",
                    event.productId(), e.getMessage());
        }
    }

    private String formatPrice(BigDecimal price) {
        return price.stripTrailingZeros().toPlainString();
    }
}
