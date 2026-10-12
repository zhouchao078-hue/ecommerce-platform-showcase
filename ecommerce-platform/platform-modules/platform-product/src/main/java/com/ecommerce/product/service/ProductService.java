package com.ecommerce.product.service;

import com.ecommerce.product.model.PdProduct;
import com.ecommerce.product.model.PdProductSku;

import java.util.List;

public interface ProductService {
    /**
     * 分页查询
     * @return 分页结果
     */
    List<PdProduct> listproducts(Long merchantId, Long categoryId, Integer pageNum, Integer pageSize);
    /** 公共商品列表，不需要商家身份 */
    List<PdProduct> listAllProducts(Long categoryId, Integer pageNum, Integer pageSize);
    /** 商家主页的商品列表（公开），只含已上架商品 */
    List<PdProduct> listShopProducts(Long merchantId, Integer pageNum, Integer pageSize);
    /** 商家在售商品数量（公开） */
    long countShopProducts(Long merchantId);
    /**
     * 商品详情
     * @return 商品数据
     */
    PdProduct getProductById(Long id);;
    /**
     * 新增商品
     * @return 新增商品
     */
    void addproduct(PdProduct pdProduct);
    /**
     * 修改商品
     * @return 修改商品
     */
    void updateproduct(PdProduct pdProduct,Long merchantId);
    /**
     * 删除商品
     * @return 删除商品
     */
    void deleteproduct(Long productId,Long merchantId);
    /**
     * 商品上下架（0=下架 1=上架）
     * @param productId 商品ID
     * @param status 目标状态
     * @param merchantId 商家ID（属主校验）
     */
    void updateStatus(Long productId, Integer status, Long merchantId);
    /**
     * 售后退货入库：原子回补库存并失效缓存
     */
    void restoreStock(Long productId, Integer quantity);
    /** 查询启用中的 SKU */
    PdProductSku getSku(Long skuId);
    List<PdProductSku> listActiveSkus(Long productId);
    /** 下单扣 SKU 库存，并同步商品聚合库存 */
    void deductSkuStock(Long skuId, Integer quantity);
    /** 取消/退货回补 SKU 库存，并同步商品聚合库存 */
    void restoreSkuStock(Long skuId, Integer quantity);
    /**
     * 模糊搜索
     * @return 商品列表
     */
    List<PdProduct> searchProducts(String keyword, Integer pageNum, Integer pageSize);
}
