package com.ecommerce.merchant.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.api.merchant.dto.ApplyMerchantDTO;
import com.ecommerce.api.merchant.vo.MerchantStatusVO;
import com.ecommerce.merchant.model.MdMerchant;

public interface MerchantService {
    /**
     * 获取商家状态
     *
     * @param userId 用户ID
     * @return 用户资料
     */
    MerchantStatusVO getStatus(Long userId);
    /**
     * 查看申请
     */
    void apply(Long userId, ApplyMerchantDTO dto);

    /**
     * 管理员：分页查询商家列表（可按状态筛选）
     *
     * @param status   状态（0待审核/1已开通/2已拒绝，null=全部）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<MdMerchant> listMerchants(Integer status, Integer pageNum, Integer pageSize);

    /**
     * 管理员：审核商家（0待审核 → 1通过 / 2拒绝）
     * 通过时授予该商家用户 ROLE_MERCHANT 角色
     *
     * @param merchantId 商家ID
     * @param status     目标状态（1=通过 2=拒绝）
     */
    void reviewMerchant(Long merchantId, Integer status);

    /** 根据商家 ID 查询所属用户 ID；不存在时返回 null */
    Long getUserIdByMerchantId(Long merchantId);
}
