package com.ecommerce.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.api.product.dto.Categorydto;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.framework.cache.RedisCacheService;
import com.ecommerce.merchant.mapper.MdMerchantMapper;
import com.ecommerce.merchant.model.MdMerchant;
import com.ecommerce.product.mapper.CategoryMapper;
import com.ecommerce.product.model.PdCategory;
import com.ecommerce.product.model.PdProduct;
import com.ecommerce.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryMapper categoryMapper;
    private final MdMerchantMapper mdMerchantMapper;
    private final RedisCacheService cache;

    private static final Duration CATEGORY_TTL = Duration.ofMinutes(10);
    private static final Duration NULL_TTL = Duration.ofSeconds(30);

    @Override
    public List<PdCategory> listCategories(Long merchantId) {
        String key = "ecommerce:category:merchant:" + merchantId;
        List<PdCategory> cached = cache.getListJson(key, PdCategory.class);
        if (cached != null) return cached;
        if (cache.isNullValue(key)) return List.of();
        QueryWrapper<PdCategory> wrapper = new QueryWrapper<>();
        wrapper.eq("merchant_id", merchantId)
                .orderByAsc("sort_order");
        List<PdCategory> categories = categoryMapper.selectList(wrapper);
        cacheCategories(key, categories);
        return categories;
    }
    @Override
    public List<PdCategory> listAllCategories() {
        String key = "ecommerce:category:public";
        List<PdCategory> cached = cache.getListJson(key, PdCategory.class);
        if (cached != null) return cached;
        if (cache.isNullValue(key)) return List.of();
        QueryWrapper<PdCategory> wrapper = new QueryWrapper<>();
        wrapper.orderByAsc("sort_order");
        List<PdCategory> categories = categoryMapper.selectList(wrapper);
        cacheCategories(key, categories);
        return categories;
    }

    @Override
    public Long getMerchantIdByUserId(Long userId){
        String key = "ecommerce:merchant:user:" + userId;
        Long cached = cache.getJson(key, Long.class);
        if (cached != null) return cached;
        if (cache.isNullValue(key)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "merchant not found");
        }
        QueryWrapper<MdMerchant> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        MdMerchant merchant = mdMerchantMapper.selectOne(wrapper);
        if (merchant == null) {
            cache.setNull(key, NULL_TTL);
            throw new BizException(ErrorCode.BAD_REQUEST, "您还不是商家");
        }
        cache.setJson(key, merchant.getId(), CATEGORY_TTL);
        return merchant.getId();
    }
    @Override
    public void createCategory(Long merchantId, String name, Long parentId, Integer sortOrder) {
        PdCategory category = new PdCategory();
        category.setMerchantId(merchantId);
        category.setName(name);
        category.setParentId(parentId);
        category.setSortOrder(sortOrder != null ? sortOrder : 0);
        category.setCreateTime(LocalDateTime.now());
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.insert(category);
        invalidateCategoryCache();
    }
    @Override
    public void updateCategory(Long id, Long merchantId, String name, Long parentId, Integer sortOrder){
        PdCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!category.getMerchantId().equals(merchantId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权操作此品类");
        }
        category.setName(name);
        category.setParentId(parentId);
        category.setSortOrder(sortOrder != null ? sortOrder : 0);
        category.setUpdateTime(LocalDateTime.now());
        categoryMapper.updateById(category);
        invalidateCategoryCache();
    }
    @Override
    public void deleteCategory(Long id, Long merchantId) {
        PdCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!category.getMerchantId().equals(merchantId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权操作此品类");
        }
        categoryMapper.deleteById(id);
        invalidateCategoryCache();
    }

    private void cacheCategories(String key, List<PdCategory> categories) {
        if (categories == null || categories.isEmpty()) cache.setNull(key, NULL_TTL);
        else cache.setJson(key, categories, CATEGORY_TTL);
    }

    private void invalidateCategoryCache() {
        cache.deleteByPattern("ecommerce:category:*");
        cache.deleteByPattern("ecommerce:merchant:*");
    }
}
