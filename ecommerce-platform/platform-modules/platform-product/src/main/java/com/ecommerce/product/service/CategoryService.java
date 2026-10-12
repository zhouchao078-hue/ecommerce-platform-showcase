package com.ecommerce.product.service;

import com.ecommerce.api.product.dto.Categorydto;
import com.ecommerce.product.model.PdCategory;

import java.util.List;

public interface CategoryService {
    /** 公共分类列表 */
    List<PdCategory> listAllCategories();
    /**
     * 查询品类
     * @param merchantId 商家ID
     * @return 查询品类
     */
    List<PdCategory> listCategories(Long merchantId);
    /**
     *
     * @param merchantId,dto 商家id
     * @return 新增品类
     */
    void createCategory(Long merchantId, String name, Long parentId, Integer sortOrder);
    /**
     *
     * @param userId
     * @return 根据用户id查询merchantId
     */
    Long getMerchantIdByUserId(Long userId);
    /**
     *
     * @param  id,  merchantId,  dto
     *
     */
    void updateCategory(Long id,Long merchantId, String name, Long parentId, Integer sortOrder);
    /**
     *
     * @param id merchantId
     *
     */
    void deleteCategory(Long id, Long merchantId);
}
