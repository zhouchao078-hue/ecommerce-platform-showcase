package com.ecommerce.order.service.impl;

import com.ecommerce.order.mapper.MerchantDashboardMapper;
import com.ecommerce.order.model.MerchantDashboardVO;
import com.ecommerce.order.service.MerchantDashboardService;
import com.ecommerce.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantDashboardServiceImpl implements MerchantDashboardService {

    private final MerchantDashboardMapper dashboardMapper;
    private final CategoryService categoryService;

    @Override
    public MerchantDashboardVO getDashboard(Long userId) {
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        MerchantDashboardVO vo = dashboardMapper.selectSummary(merchantId);
        vo.setRecentOrders(dashboardMapper.selectRecentOrders(merchantId));
        return vo;
    }
}
