package com.ecommerce.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.mapper.PaymentMapper;
import com.ecommerce.order.model.OrderVO;
import com.ecommerce.order.model.Payment;
import com.ecommerce.order.model.PaymentVO;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentMapper paymentMapper;
    private final OrderService orderService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO createPayment(Long userId, Long orderId, Integer channel) {
        // 订单服务里已经做了归属校验，直接复用它
        OrderVO order = orderService.getOrderDetail(orderId, userId);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT.getCode()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该订单当前不可支付");
        }

        // 已经有未支付的支付单就复用，避免用户反复点「去支付」生成一堆支付单
        QueryWrapper<Payment> wrapper = new QueryWrapper<>();
        wrapper.eq("order_id", orderId)
                .eq("status", Payment.STATUS_PENDING)
                .orderByDesc("id")
                .last("limit 1");
        Payment existing = paymentMapper.selectOne(wrapper);
        if (existing != null) {
            return toVO(existing);
        }

        Payment payment = new Payment();
        payment.setPaymentNo(generatePaymentNo());
        payment.setOrderId(orderId);
        payment.setUserId(userId);
        // 金额用订单当前金额做快照，后续订单被改也不影响这笔支付
        payment.setAmount(order.getTotalAmount());
        payment.setChannel(channel == null ? Payment.CHANNEL_MOCK : channel);
        payment.setStatus(Payment.STATUS_PENDING);
        payment.setCreateTime(LocalDateTime.now());
        payment.setUpdateTime(LocalDateTime.now());
        paymentMapper.insert(payment);

        log.info("创建支付单: paymentNo={}, orderId={}, amount={}", payment.getPaymentNo(), orderId, payment.getAmount());
        return toVO(payment);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentVO pay(String paymentNo, Long userId) {
        Payment payment = loadOwned(paymentNo, userId);

        // 幂等：已支付成功就直接返回，不重复处理
        // （真实场景里第三方会重复回调，这一步是必须的）
        if (payment.getStatus() == Payment.STATUS_SUCCESS) {
            log.info("支付单已支付，幂等返回: paymentNo={}", paymentNo);
            return toVO(payment);
        }
        if (payment.getStatus() == Payment.STATUS_CLOSED) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该支付单已关闭，请重新发起支付");
        }

        // 校验订单是否还能支付（可能已被超时任务取消）
        OrderVO order = orderService.getOrderDetail(payment.getOrderId(), userId);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT.getCode()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "订单已不可支付（可能已取消），请重新下单");
        }

        LocalDateTime now = LocalDateTime.now();
        payment.setStatus(Payment.STATUS_SUCCESS);
        payment.setPaidAt(now);
        payment.setUpdateTime(now);
        paymentMapper.updateById(payment);

        // 支付成功后推进订单状态：待付款 -> 待发货
        orderService.payOrder(payment.getOrderId(), userId);

        log.info("支付成功: paymentNo={}, orderId={}", paymentNo, payment.getOrderId());
        return toVO(payment);
    }

    @Override
    public PaymentVO getPayment(String paymentNo, Long userId) {
        return toVO(loadOwned(paymentNo, userId));
    }

    /** 按支付单号加载并校验归属，防止越权查别人的支付单 */
    private Payment loadOwned(String paymentNo, Long userId) {
        QueryWrapper<Payment> wrapper = new QueryWrapper<>();
        wrapper.eq("payment_no", paymentNo);
        Payment payment = paymentMapper.selectOne(wrapper);
        if (payment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "支付单不存在");
        }
        if (!payment.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权操作此支付单");
        }
        return payment;
    }

    private String generatePaymentNo() {
        return "PAY" + LocalDateTime.now().format(NO_FMT)
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

    private PaymentVO toVO(Payment p) {
        return PaymentVO.builder()
                .paymentNo(p.getPaymentNo())
                .orderId(p.getOrderId())
                .amount(p.getAmount())
                .status(p.getStatus())
                .statusDesc(switch (p.getStatus()) {
                    case Payment.STATUS_SUCCESS -> "支付成功";
                    case Payment.STATUS_CLOSED -> "已关闭";
                    default -> "待支付";
                })
                .paidAt(p.getPaidAt())
                .build();
    }
}
