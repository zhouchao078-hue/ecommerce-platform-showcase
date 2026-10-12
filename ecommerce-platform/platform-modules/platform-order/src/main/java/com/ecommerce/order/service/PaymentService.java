package com.ecommerce.order.service;

import com.ecommerce.order.model.PaymentVO;

public interface PaymentService {

    /**
     * 创建支付单。
     * 同一订单重复调用会复用那张未支付的支付单，不会生成一堆垃圾支付单。
     */
    PaymentVO createPayment(Long userId, Long orderId, Integer channel);

    /**
     * 支付成功。
     * 真实项目里这个动作由第三方支付平台的异步回调触发，这里用接口模拟。
     * 必须幂等：重复调用不能重复改状态、重复处理。
     */
    PaymentVO pay(String paymentNo, Long userId);

    /** 查询支付单 */
    PaymentVO getPayment(String paymentNo, Long userId);
}
