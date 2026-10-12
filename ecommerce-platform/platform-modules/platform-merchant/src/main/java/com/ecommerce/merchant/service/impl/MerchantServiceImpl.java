package com.ecommerce.merchant.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.api.merchant.dto.ApplyMerchantDTO;
import com.ecommerce.api.merchant.vo.MerchantStatusVO;
import com.ecommerce.auth.mapper.SysRoleMapper;
import com.ecommerce.auth.mapper.SysUserRoleMapper;
import com.ecommerce.auth.model.SysRole;
import com.ecommerce.auth.model.SysUserRole;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.merchant.enums.MerchantStatus;
import com.ecommerce.merchant.mapper.MdMerchantMapper;
import com.ecommerce.merchant.model.MdMerchant;
import com.ecommerce.merchant.service.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {

    private final MdMerchantMapper mdMerchantMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    /** 商家角色编码 */
    private static final String ROLE_MERCHANT = "ROLE_MERCHANT";

    @Override
    public MerchantStatusVO getStatus(Long userId) {
        log.info("查询商家状态: userId={}", userId);

        // 按 user_id 查询商家记录
        QueryWrapper<MdMerchant> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        MdMerchant merchant = mdMerchantMapper.selectOne(wrapper);

        if (merchant == null) {
            // 没有申请过
            log.info("用户未申请商家: userId={}", userId);
            return MerchantStatusVO.builder()
                    .userId(userId)
                    .status(-1)
                    .statusDesc("未申请")
                    .build();
        }

        // 查到了，用枚举把数字转成中文描述
        MerchantStatus statusEnum = MerchantStatus.fromCode(merchant.getStatus());
        log.info("商家状态查询成功: userId={}, status={}", userId, statusEnum.getDesc());

        return MerchantStatusVO.builder()
                .userId(userId)
                .status(merchant.getStatus())
                .statusDesc(statusEnum.getDesc())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void apply(Long userId, ApplyMerchantDTO dto) {
        QueryWrapper<MdMerchant> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        MdMerchant existing  = mdMerchantMapper.selectOne(wrapper);

        if (existing != null && (existing.getStatus() == null || existing.getStatus() != 2)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "您已申请过商家");
        }
        if (existing != null) {
            existing.setShopName(dto.getShopName());
            existing.setDescription(dto.getDescription());
            existing.setStatus(MerchantStatus.PENDING.getCode());
            existing.setUpdateTime(LocalDateTime.now());
            mdMerchantMapper.updateById(existing);
            return;
        }
        MdMerchant merchant = new MdMerchant();
        merchant.setUserId(userId);
        merchant.setShopName(dto.getShopName());
        merchant.setDescription(dto.getDescription());
        merchant.setStatus(0);  // 待审核
        merchant.setCreateTime(LocalDateTime.now());
        merchant.setUpdateTime(LocalDateTime.now());
        mdMerchantMapper.insert(merchant);

    }

    @Override
    public IPage<MdMerchant> listMerchants(Integer status, Integer pageNum, Integer pageSize) {
        QueryWrapper<MdMerchant> wrapper = new QueryWrapper<>();
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        return mdMerchantMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewMerchant(Long merchantId, Integer status) {
        if (status == null || (status != 1 && status != 2)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "审核状态只能是 1（通过）或 2（拒绝）");
        }
        MdMerchant merchant = mdMerchantMapper.selectById(merchantId);
        if (merchant == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商家申请不存在");
        }
        if (merchant.getStatus() != null && merchant.getStatus() != MerchantStatus.PENDING.getCode()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该申请已审核，不能重复审核");
        }

        // 1. 更新商家状态
        merchant.setStatus(status);
        merchant.setUpdateTime(LocalDateTime.now());
        mdMerchantMapper.updateById(merchant);
        log.info("商家审核完成: merchantId={}, status={}", merchantId, status);

        // 2. 审核通过时，授予该用户 ROLE_MERCHANT 角色（开通完整权限）
        if (status == MerchantStatus.ACTIVE.getCode()) {
            grantMerchantRole(merchant.getUserId());
        }
    }

    @Override
    public Long getUserIdByMerchantId(Long merchantId) {
        if (merchantId == null) return null;
        MdMerchant merchant = mdMerchantMapper.selectById(merchantId);
        return merchant == null ? null : merchant.getUserId();
    }

    /** 给用户授予商家角色（幂等：已拥有则跳过） */
    private void grantMerchantRole(Long userId) {
        SysRole role = sysRoleMapper.selectByCode(ROLE_MERCHANT);
        if (role == null) {
            log.error("ROLE_MERCHANT 角色不存在，请检查 sys_role 初始化数据");
            return;
        }
        QueryWrapper<SysUserRole> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).eq("role_id", role.getId());
        if (sysUserRoleMapper.selectCount(wrapper) > 0) {
            return; // 已授权，跳过
        }
        SysUserRole rel = new SysUserRole();
        rel.setUserId(userId);
        rel.setRoleId(role.getId());
        sysUserRoleMapper.insert(rel);
        log.info("商家审核通过，已授予 ROLE_MERCHANT: userId={}", userId);
    }

}
