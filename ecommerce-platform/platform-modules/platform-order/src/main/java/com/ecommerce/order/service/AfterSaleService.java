package com.ecommerce.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.order.model.AfterSale;

import java.math.BigDecimal;
import java.util.List;

public interface AfterSaleService {

    /** 买家申请售后：type 1=仅退款，2=退货退款 */
    AfterSale apply(Long userId, Long orderItemId, Integer type,
                    String reason, String description, BigDecimal amount);

    List<AfterSale> listMine(Long userId);

    AfterSale getMine(Long id, Long userId);

    /** 买家撤销申请（仅待审核可撤） */
    void cancel(Long id, Long userId);

    /** 退货退款：买家填写寄回物流 */
    void submitReturn(Long id, Long userId, String returnCompany, String returnTrackingNo);

    /** 商家侧：我店铺的售后 */
    IPage<AfterSale> listMerchant(Long userId, Integer status, Integer pageNum, Integer pageSize);

    /** 商家同意：仅退款立即触发退款；退货退款只放行买家寄回（幂等） */
    void approve(Long id, Long userId);

    /** 商家拒绝 */
    void reject(Long id, Long userId, String reason);

    /** 退货退款：商家确认收到退货，退款并回补库存 */
    void receiveReturn(Long id, Long userId);
}
