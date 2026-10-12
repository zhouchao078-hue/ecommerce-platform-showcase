package com.ecommerce.order.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO {
    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private Integer status;
    private String statusDesc;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private LocalDateTime createTime;
    private List<OrderItem> items;
    /** PARENT 订单会有多个商家子订单 */
    private List<SubOrderVO> subOrders;
    private Long parentOrderId;
    private Long merchantId;
    private String orderType;
}
