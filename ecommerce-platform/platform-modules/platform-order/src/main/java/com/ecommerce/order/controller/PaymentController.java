package com.ecommerce.order.controller;

import com.ecommerce.auth.service.AuthService;
import com.ecommerce.common.result.Result;
import com.ecommerce.order.model.PaymentVO;
import com.ecommerce.order.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 支付。
 *
 * 这里把「创建支付单」和「支付成功」拆成两步，是为了尽可能贴近真实结构：
 * 真实项目里第二步由第三方支付平台的异步回调触发，
 * /pay 这个接口只是把那个回调搬到了本地，方便没有支付资质时也能跑通全链路。
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final AuthService authService;

    /** 创建支付单（用户点「去支付」时调用） */
    @PostMapping
    public Result<PaymentVO> create(@RequestBody Map<String, Object> body) {
        Long orderId = Long.valueOf(String.valueOf(body.get("orderId")));
        Integer channel = body.get("channel") == null
                ? null
                : Integer.valueOf(String.valueOf(body.get("channel")));
        return Result.success(paymentService.createPayment(authService.getCurrentUserId(), orderId, channel));
    }

    /** 模拟支付成功（真实项目里对应第三方回调，必须幂等） */
    @PostMapping("/{paymentNo}/pay")
    public Result<PaymentVO> pay(@PathVariable String paymentNo) {
        return Result.success(paymentService.pay(paymentNo, authService.getCurrentUserId()));
    }

    /** 查询支付单状态 */
    @GetMapping("/{paymentNo}")
    public Result<PaymentVO> get(@PathVariable String paymentNo) {
        return Result.success(paymentService.getPayment(paymentNo, authService.getCurrentUserId()));
    }
}
