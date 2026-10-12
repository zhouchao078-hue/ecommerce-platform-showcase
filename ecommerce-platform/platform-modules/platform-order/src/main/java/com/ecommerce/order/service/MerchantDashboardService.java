package com.ecommerce.order.service;

import com.ecommerce.order.model.MerchantDashboardVO;

public interface MerchantDashboardService {
    MerchantDashboardVO getDashboard(Long userId);
}
