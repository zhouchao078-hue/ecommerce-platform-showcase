package com.ecommerce.order.model;

import lombok.Data;

import java.util.List;

/** 订单分页缓存载体，避免把 MyBatis-Plus Page 的内部字段直接暴露给 Redis。 */
@Data
public class OrderPageCache {
    private long current;
    private long size;
    private long total;
    private List<Order> records;
}
