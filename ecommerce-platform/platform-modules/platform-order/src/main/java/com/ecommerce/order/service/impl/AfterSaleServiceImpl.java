package com.ecommerce.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.order.mapper.*;
import com.ecommerce.order.model.*;
import com.ecommerce.order.service.AfterSaleService;
import com.ecommerce.merchant.service.MerchantService;
import com.ecommerce.product.service.CategoryService;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.user.model.Notification;
import com.ecommerce.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class AfterSaleServiceImpl implements AfterSaleService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 只有这几个状态说明钱已经付过了，才能谈退款 */
    private static final List<Integer> REFUNDABLE_ORDER_STATUS =
            List.of(1, 2, 3); // 待发货 / 待收货 / 已完成
    /** 未发货订单无法走退货，只能走仅退款 */
    private static final List<Integer> RETURNABLE_ORDER_STATUS =
            List.of(2, 3); // 待收货 / 已完成

    private final AfterSaleMapper afterSaleMapper;
    private final RefundMapper refundMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderMapper orderMapper;
    private final PaymentMapper paymentMapper;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final MerchantService merchantService;
    private final NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AfterSale apply(Long userId, Long orderItemId, Integer type,
                           String reason, String description, BigDecimal amount) {
        int afterSaleType = type == null ? AfterSale.TYPE_REFUND_ONLY : type;
        if (afterSaleType != AfterSale.TYPE_REFUND_ONLY
                && afterSaleType != AfterSale.TYPE_RETURN_REFUND) {
            throw new BizException(ErrorCode.BAD_REQUEST, "售后类型不合法");
        }

        QueryWrapper<OrderItem> itemLock = new QueryWrapper<>();
        itemLock.eq("id", orderItemId).last("FOR UPDATE");
        OrderItem item = orderItemMapper.selectOne(itemLock);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单明细不存在");
        }
        Order order = orderMapper.selectById(item.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权对此订单申请售后");
        }
        if (!REFUNDABLE_ORDER_STATUS.contains(order.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该订单当前状态不支持申请售后");
        }
        if (afterSaleType == AfterSale.TYPE_RETURN_REFUND
                && !RETURNABLE_ORDER_STATUS.contains(order.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品发货后才能申请退货退款");
        }

        // 同一条明细不能再开一笔售后，除非上一笔是「已拒绝」或「已撤销」。
        // 注意必须把 DONE 也算进去：只挡 PENDING/APPROVED 的话，
        // 买家拿到退款后可以再申请一次，退第二遍。
        QueryWrapper<AfterSale> active = new QueryWrapper<>();
        active.eq("order_item_id", orderItemId).in("status",
                AfterSale.STATUS_PENDING, AfterSale.STATUS_APPROVED,
                AfterSale.STATUS_DONE, AfterSale.STATUS_RETURNED);
        if (afterSaleMapper.selectCount(active) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该商品已有售后在处理中");
        }

        // 金额上限：不能超过这条明细的小计，防止拿 50 块的商品退 500
        BigDecimal req = (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
                ? item.getSubtotal() : amount;
        if (req.compareTo(item.getSubtotal()) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "退款金额不能超过该商品实付金额");
        }

        AfterSale as = new AfterSale();
        as.setAfterSaleNo(generateNo("AS"));
        as.setOrderId(item.getOrderId());
        as.setOrderItemId(orderItemId);
        as.setUserId(userId);
        // 商家归属取订单明细的快照，商品删除后也能正确路由给商家
        as.setMerchantId(item.getMerchantId());
        as.setType(afterSaleType);
        as.setReason(reason);
        as.setDescription(description);
        as.setAmount(req);
        as.setStatus(AfterSale.STATUS_PENDING);
        as.setAppliedAt(LocalDateTime.now());
        as.setCreateTime(LocalDateTime.now());
        as.setUpdateTime(LocalDateTime.now());
        afterSaleMapper.insert(as);

        log.info("售后申请已提交: afterSaleNo={}, orderItemId={}, amount={}",
                as.getAfterSaleNo(), orderItemId, req);

        Long merchantUserId = merchantService.getUserIdByMerchantId(item.getMerchantId());
        if (merchantUserId != null) {
            notificationService.send(
                    merchantUserId,
                    Notification.TYPE_AFTER_SALE,
                    "有新的售后申请",
                    String.format("订单「%s」有一笔新的售后申请，请及时处理。", order.getOrderNo()),
                    Notification.REF_ORDER,
                    order.getId(),
                    "as" + as.getId() + "-apply"
            );
        }
        return as;
    }

    @Override
    public List<AfterSale> listMine(Long userId) {
        QueryWrapper<AfterSale> w = new QueryWrapper<>();
        w.eq("user_id", userId).orderByDesc("id");
        return afterSaleMapper.selectList(w);
    }

    @Override
    public AfterSale getMine(Long id, Long userId) {
        AfterSale as = afterSaleMapper.selectById(id);
        if (as == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "售后单不存在");
        }
        if (!as.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权查看此售后单");
        }
        return as;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, Long userId) {
        AfterSale as = loadMineForUpdate(id, userId);
        if (as.getStatus() != AfterSale.STATUS_PENDING) {
            throw new BizException(ErrorCode.BAD_REQUEST, "只有待审核的申请可以撤销");
        }
        as.setStatus(AfterSale.STATUS_CANCELLED);
        as.setHandledAt(LocalDateTime.now());
        as.setUpdateTime(LocalDateTime.now());
        afterSaleMapper.updateById(as);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitReturn(Long id, Long userId, String returnCompany, String returnTrackingNo) {
        AfterSale as = loadMineForUpdate(id, userId);
        if (as.getType() != AfterSale.TYPE_RETURN_REFUND) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该售后单不是退货退款");
        }
        if (as.getStatus() != AfterSale.STATUS_APPROVED) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商家同意退货后才能填写物流");
        }
        String company = returnCompany == null ? "" : returnCompany.trim();
        String trackingNo = returnTrackingNo == null ? "" : returnTrackingNo.trim();
        if (company.isEmpty() || trackingNo.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请填写退货物流公司和物流单号");
        }
        if (company.length() > 50 || trackingNo.length() > 100) {
            throw new BizException(ErrorCode.BAD_REQUEST, "退货物流信息过长");
        }

        LocalDateTime now = LocalDateTime.now();
        as.setReturnCompany(company);
        as.setReturnTrackingNo(trackingNo);
        as.setReturnedAt(now);
        as.setStatus(AfterSale.STATUS_RETURNED);
        as.setUpdateTime(now);
        afterSaleMapper.updateById(as);

        Long merchantUserId = merchantService.getUserIdByMerchantId(as.getMerchantId());
        if (merchantUserId != null) {
            notificationService.send(
                    merchantUserId,
                    Notification.TYPE_AFTER_SALE,
                    "买家已寄回商品",
                    String.format("售后单「%s」已填写退货物流，请收到商品后确认收货。",
                            as.getAfterSaleNo()),
                    Notification.REF_ORDER,
                    as.getOrderId(),
                    "as" + as.getId() + "-shipped"
            );
        }
    }

    @Override
    public IPage<AfterSale> listMerchant(Long userId, Integer status, Integer pageNum, Integer pageSize) {
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        QueryWrapper<AfterSale> w = new QueryWrapper<>();
        w.eq("merchant_id", merchantId);
        if (status != null) {
            w.eq("status", status);
        }
        w.orderByDesc("id");
        return afterSaleMapper.selectPage(
                new Page<>(pageNum == null ? 1 : pageNum, pageSize == null ? 10 : pageSize), w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, Long userId) {
        AfterSale as = loadForMerchantForUpdate(id, userId);

        // 幂等：已完成就直接返回，不重复退款
        if (as.getStatus() == AfterSale.STATUS_DONE) {
            log.info("售后已完成，幂等返回: afterSaleNo={}", as.getAfterSaleNo());
            return;
        }
        if (as.getType() == AfterSale.TYPE_RETURN_REFUND
                && (as.getStatus() == AfterSale.STATUS_APPROVED
                || as.getStatus() == AfterSale.STATUS_RETURNED)) {
            return;
        }
        if (as.getStatus() != AfterSale.STATUS_PENDING) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该申请当前不可同意");
        }

        if (as.getType() == AfterSale.TYPE_RETURN_REFUND) {
            LocalDateTime now = LocalDateTime.now();
            as.setStatus(AfterSale.STATUS_APPROVED);
            as.setHandledAt(now);
            as.setUpdateTime(now);
            afterSaleMapper.updateById(as);

            notificationService.send(
                    as.getUserId(),
                    Notification.TYPE_AFTER_SALE,
                    "退货申请已通过",
                    "商家已同意退货，请尽快填写物流信息并寄回商品。",
                    Notification.REF_ORDER,
                    as.getOrderId(),
                    "as" + as.getId() + "-return"
            );
            return;
        }

        completeRefund(as);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveReturn(Long id, Long userId) {
        AfterSale as = loadForMerchantForUpdate(id, userId);
        if (as.getType() != AfterSale.TYPE_RETURN_REFUND) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该售后单不是退货退款");
        }
        if (as.getStatus() == AfterSale.STATUS_DONE) {
            return;
        }
        if (as.getStatus() != AfterSale.STATUS_RETURNED) {
            throw new BizException(ErrorCode.BAD_REQUEST, "买家尚未寄回或物流尚未填写");
        }

        OrderItem item = orderItemMapper.selectById(as.getOrderItemId());
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单明细不存在");
        }
        if (item.getSkuId() != null) {
            productService.restoreSkuStock(item.getSkuId(), item.getQuantity());
        } else {
            productService.restoreStock(item.getProductId(), item.getQuantity());
        }

        LocalDateTime now = LocalDateTime.now();
        as.setReceivedAt(now);
        as.setUpdateTime(now);
        completeRefund(as);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, Long userId, String reason) {
        AfterSale as = loadForMerchantForUpdate(id, userId);
        if (as.getStatus() != AfterSale.STATUS_PENDING) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该申请当前不可拒绝");
        }
        as.setStatus(AfterSale.STATUS_REJECTED);
        as.setRejectReason(reason);
        as.setHandledAt(LocalDateTime.now());
        as.setUpdateTime(LocalDateTime.now());
        afterSaleMapper.updateById(as);
    }

    private void completeRefund(AfterSale as) {
        if (as.getStatus() == AfterSale.STATUS_DONE) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        Refund refund = new Refund();
        refund.setRefundNo(generateNo("RF"));
        refund.setAfterSaleId(as.getId());
        refund.setOrderId(as.getOrderId());
        refund.setAmount(as.getAmount());
        refund.setStatus(Refund.STATUS_REFUNDING);
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        Order saleOrder = orderMapper.selectById(as.getOrderId());
        Long paymentOrderId = saleOrder != null && saleOrder.getParentOrderId() != null
                ? saleOrder.getParentOrderId() : as.getOrderId();
        QueryWrapper<Payment> pw = new QueryWrapper<>();
        pw.eq("order_id", paymentOrderId).eq("status", Payment.STATUS_SUCCESS).last("limit 1");
        Payment payment = paymentMapper.selectOne(pw);
        if (payment != null) {
            refund.setPaymentNo(payment.getPaymentNo());
        }
        refundMapper.insert(refund);

        refund.setStatus(Refund.STATUS_SUCCESS);
        refund.setRefundedAt(now);
        refund.setUpdateTime(now);
        refundMapper.updateById(refund);

        as.setStatus(AfterSale.STATUS_DONE);
        as.setHandledAt(now);
        as.setUpdateTime(now);
        afterSaleMapper.updateById(as);

        log.info("售后退款完成: afterSaleNo={}, refundNo={}, amount={}, type={}",
                as.getAfterSaleNo(), refund.getRefundNo(), refund.getAmount(), as.getType());

        notificationService.send(
                as.getUserId(),
                Notification.TYPE_AFTER_SALE,
                "退款已到账",
                String.format("你的售后申请已完成，¥%s 已原路退回。", as.getAmount()),
                Notification.REF_ORDER,
                as.getOrderId(),
                "as" + as.getId() + "-refund"
        );
    }

    /** 买家侧加载并校验归属，同时锁定售后单，防止并发提交 */
    private AfterSale loadMineForUpdate(Long id, Long userId) {
        QueryWrapper<AfterSale> w = new QueryWrapper<>();
        w.eq("id", id).last("FOR UPDATE");
        AfterSale as = afterSaleMapper.selectOne(w);
        if (as == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "售后单不存在");
        }
        if (!as.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权操作此售后单");
        }
        return as;
    }

    /** 商家侧加载并校验归属，同时锁定售后单，防止重复退款或重复回补库存 */
    private AfterSale loadForMerchantForUpdate(Long id, Long userId) {
        QueryWrapper<AfterSale> w = new QueryWrapper<>();
        w.eq("id", id).last("FOR UPDATE");
        AfterSale as = afterSaleMapper.selectOne(w);
        if (as == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "售后单不存在");
        }
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        if (!merchantId.equals(as.getMerchantId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "此售后单不属于你的店铺");
        }
        return as;
    }

    private String generateNo(String prefix) {
        return prefix + LocalDateTime.now().format(NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }
}
