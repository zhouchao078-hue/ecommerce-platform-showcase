package com.ecommerce.order.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 商家数据看板读模型。 */
@Data
public class MerchantDashboardVO {
    private BigDecimal totalSales;
    private BigDecimal todaySales;
    private long productTotal;
    private long productOnSale;
    private long totalOrders;
    private long pendingShip;
    private long pendingReceive;
    private long completedOrders;
    private long pendingAfterSales;
    private long returnWaitingReceipt;
    private long followerCount;
    private List<MerchantRecentOrderVO> recentOrders;
}
