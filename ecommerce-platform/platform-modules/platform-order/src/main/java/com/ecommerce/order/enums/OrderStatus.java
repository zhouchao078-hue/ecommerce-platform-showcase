package com.ecommerce.order.enums;

public enum OrderStatus {
    PENDING_PAYMENT(0, "待付款"),
    PENDING_SHIP(1, "待发货"),
    SHIPPED(2, "待收货"),
    COMPLETED(3, "已完成"),
    CANCELLED(4, "已取消");

    private final int code;
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() { return code; }
    public String getDesc() { return desc; }

    public static OrderStatus fromCode(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) return s;
        }
        throw new IllegalArgumentException("未知订单状态: " + code);
    }
    public static OrderStatus canTransitTo(int target) {
        for (OrderStatus s : values()) {
            if (s.code == target) return s;
        }
        throw new IllegalArgumentException("未知订单状态："+ target);
    }
}
