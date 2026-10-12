package com.ecommerce.product.service.impl;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.framework.cache.RedisCacheService;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.mapper.ProductSkuMapper;
import com.ecommerce.product.model.PdProduct;
import com.ecommerce.product.model.PdProductSku;
import com.ecommerce.product.event.ProductPriceDropEvent;
import com.ecommerce.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final RedisCacheService cache;
    private final ApplicationEventPublisher eventPublisher;

    private static final Duration PRODUCT_TTL = Duration.ofMinutes(10);
    private static final Duration NULL_TTL = Duration.ofSeconds(30);

    @Override
    public List<PdProduct> listAllProducts(Long categoryId, Integer pageNum, Integer pageSize) {
        String key = "ecommerce:product:list:public:category:" + (categoryId == null ? "all" : categoryId)
                + ":page:" + pageNum + ":size:" + pageSize;
        List<PdProduct> cached = cache.getListJson(key, PdProduct.class);
        if (cached != null) return cached;
        if (cache.isNullValue(key)) return List.of();
        QueryWrapper<PdProduct> wrapper = new QueryWrapper<>();
        if (categoryId != null) wrapper.eq("category_id", categoryId);
        // 公开列表只展示已上架商品
        wrapper.eq("status", 1);
        wrapper.orderByDesc("created_at");
        Page<PdProduct> page = new Page<>(pageNum, pageSize);
        List<PdProduct> products = productMapper.selectPage(page, wrapper).getRecords();
        cacheProducts(key, products);
        return products;
    }

    @Override
    public List<PdProduct> listShopProducts(Long merchantId, Integer pageNum, Integer pageSize) {
        String key = "ecommerce:product:list:shop:" + merchantId
                + ":page:" + pageNum + ":size:" + pageSize;
        List<PdProduct> cached = cache.getListJson(key, PdProduct.class);
        if (cached != null) return cached;
        if (cache.isNullValue(key)) return List.of();
        QueryWrapper<PdProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("merchant_id", merchantId);
        // 商家主页是公开页面，只展示已上架商品
        wrapper.eq("status", 1);
        wrapper.orderByDesc("created_at");
        Page<PdProduct> page = new Page<>(pageNum, pageSize);
        List<PdProduct> products = productMapper.selectPage(page, wrapper).getRecords();
        cacheProducts(key, products);
        return products;
    }

    @Override
    public long countShopProducts(Long merchantId) {
        QueryWrapper<PdProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("merchant_id", merchantId).eq("status", 1);
        return productMapper.selectCount(wrapper);
    }

    @Override
    public List<PdProduct> listproducts(Long merchantId, Long categoryId, Integer pageNum, Integer pageSize) {
        String key = "ecommerce:product:list:merchant:" + merchantId + ":category:"
                + (categoryId == null ? "all" : categoryId) + ":page:" + pageNum + ":size:" + pageSize;
        List<PdProduct> cached = cache.getListJson(key, PdProduct.class);
        if (cached != null) return cached;
        if (cache.isNullValue(key)) return List.of();
        QueryWrapper<PdProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("merchant_id", merchantId);
        if (categoryId != null) {
            wrapper.eq("category_id", categoryId);
        }
        wrapper.orderByDesc("created_at");

        Page<PdProduct> page = new Page<>(pageNum, pageSize);
        Page<PdProduct> pageResult = productMapper.selectPage(page, wrapper);
        List<PdProduct> products = pageResult.getRecords();
        cacheProducts(key, products);
        return products;
    }

    @Override
    public PdProduct getProductById(Long id){
        String key = productKey(id);
        PdProduct cached = cache.getJson(key, PdProduct.class);
        if (cached != null) return cached;
        if (cache.isNullValue(key)) throw new BizException(ErrorCode.NOT_FOUND);
        PdProduct product = productMapper.selectById(id);
        if (product == null) {
            cache.setNull(key, NULL_TTL);
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        product.setSkus(listActiveSkus(id));
        cache.setJson(key, product, PRODUCT_TTL);
        return product;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addproduct(PdProduct pdProduct) {
        PdProduct product = new PdProduct();
        product.setMerchantId(pdProduct.getMerchantId());
        product.setName(pdProduct.getName());
        product.setPrice(pdProduct.getPrice() == null ? BigDecimal.ZERO : pdProduct.getPrice());
        product.setStock(pdProduct.getStock() == null ? 0 : pdProduct.getStock());
        product.setDescription(pdProduct.getDescription());
        product.setImages(pdProduct.getImages()); // JSON数组字符串
        product.setCategoryId(pdProduct.getCategoryId());
        product.setStatus(0);                                  // 默认下架
        product.setCreateTime(LocalDateTime.now());
        product.setUpdateTime(LocalDateTime.now());

        productMapper.insert(product);
        saveSkus(product.getId(), pdProduct.getSkus(), pdProduct);
        syncProductAggregate(product.getId());
        invalidateProductCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateproduct(PdProduct pdProduct,Long merchantId) {
        PdProduct existing = productMapper.selectById(pdProduct.getId());
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!existing.getMerchantId().equals(merchantId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权操作此商品");
        }
        BigDecimal oldPrice = existing.getPrice();
        BigDecimal newPrice = pdProduct.getPrice();
        Integer effectiveStatus = pdProduct.getStatus() == null
                ? existing.getStatus() : pdProduct.getStatus();
        String effectiveName = pdProduct.getName() == null
                ? existing.getName() : pdProduct.getName();

        if (pdProduct.getSkus() == null) {
            // 未提交 SKU 列表时保持聚合价格和库存，避免旧客户端把库存改成不一致值
            pdProduct.setPrice(existing.getPrice());
            pdProduct.setStock(existing.getStock());
        }
        pdProduct.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(pdProduct);
        if (pdProduct.getSkus() != null) {
            updateSkus(existing.getId(), pdProduct.getSkus(), pdProduct);
            syncProductAggregate(existing.getId());
        }
        invalidateProductCache();

        PdProduct refreshed = productMapper.selectById(existing.getId());
        boolean priceDropped = oldPrice != null && newPrice != null
                && newPrice.compareTo(oldPrice) < 0;
        if (refreshed != null) {
            priceDropped = oldPrice != null && refreshed.getPrice() != null
                    && refreshed.getPrice().compareTo(oldPrice) < 0;
        }
        if (priceDropped && Integer.valueOf(1).equals(effectiveStatus)) {
            eventPublisher.publishEvent(new ProductPriceDropEvent(
                    existing.getId(), effectiveName, oldPrice, refreshed.getPrice()));
        }
    }

    @Override
    public void deleteproduct(Long productId,Long merchantId) {
        PdProduct  product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!product.getMerchantId().equals(merchantId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权操作此商品");
        }
        productMapper.deleteById(productId);
        QueryWrapper<PdProductSku> skuWrapper = new QueryWrapper<>();
        skuWrapper.eq("product_id", productId);
        productSkuMapper.delete(skuWrapper);
        invalidateProductCache();
    }

    @Override
    public void updateStatus(Long productId, Integer status, Long merchantId) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品状态只能是 0（下架）或 1（上架）");
        }
        PdProduct product = productMapper.selectById(productId);
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        if (!product.getMerchantId().equals(merchantId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权操作此商品");
        }
        product.setStatus(status);
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        invalidateProductCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreStock(Long productId, Integer quantity) {
        if (productId == null || quantity == null || quantity <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "回补库存参数不合法");
        }
        QueryWrapper<PdProduct> wrapper = new QueryWrapper<>();
        wrapper.eq("id", productId).last("FOR UPDATE");
        PdProduct product = productMapper.selectOne(wrapper);
        if (product == null) {
            // 商品已删除时仍允许退款继续，避免历史订单卡死
            return;
        }
        int current = product.getStock() == null ? 0 : product.getStock();
        product.setStock(current + quantity);
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
        invalidateProductCache();
    }

    @Override
    public PdProductSku getSku(Long skuId) {
        PdProductSku sku = productSkuMapper.selectById(skuId);
        if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "商品规格不存在或已停用");
        }
        return sku;
    }

    @Override
    public List<PdProductSku> listActiveSkus(Long productId) {
        QueryWrapper<PdProductSku> wrapper = new QueryWrapper<>();
        wrapper.eq("product_id", productId).eq("status", 1)
                .orderByDesc("is_default").orderByAsc("id");
        return productSkuMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deductSkuStock(Long skuId, Integer quantity) {
        if (skuId == null || quantity == null || quantity <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "扣减库存参数不合法");
        }
        QueryWrapper<PdProductSku> wrapper = new QueryWrapper<>();
        wrapper.eq("id", skuId).last("FOR UPDATE");
        PdProductSku sku = productSkuMapper.selectOne(wrapper);
        if (sku == null || !Integer.valueOf(1).equals(sku.getStatus())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品规格不存在或已停用");
        }
        int current = sku.getStock() == null ? 0 : sku.getStock();
        if (current < quantity) {
            throw new BizException(ErrorCode.BAD_REQUEST, "SKU 库存不足");
        }
        sku.setStock(current - quantity);
        sku.setUpdateTime(LocalDateTime.now());
        productSkuMapper.updateById(sku);
        syncProductAggregate(sku.getProductId());
        invalidateProductCache();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreSkuStock(Long skuId, Integer quantity) {
        if (skuId == null || quantity == null || quantity <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "回补库存参数不合法");
        }
        QueryWrapper<PdProductSku> wrapper = new QueryWrapper<>();
        wrapper.eq("id", skuId).last("FOR UPDATE");
        PdProductSku sku = productSkuMapper.selectOne(wrapper);
        if (sku == null) return;
        int current = sku.getStock() == null ? 0 : sku.getStock();
        sku.setStock(current + quantity);
        sku.setUpdateTime(LocalDateTime.now());
        productSkuMapper.updateById(sku);
        syncProductAggregate(sku.getProductId());
        invalidateProductCache();
    }

    @Override
    public List<PdProduct> searchProducts(String keyword, Integer pageNum, Integer pageSize) {
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String key = "ecommerce:product:search:" + normalizedKeyword.replace(":", "_")
                + ":page:" + pageNum + ":size:" + pageSize;
        List<PdProduct> cached = cache.getListJson(key, PdProduct.class);
        if (cached != null) return cached;
        if (cache.isNullValue(key)) return List.of();
        QueryWrapper<PdProduct> wrapper = new QueryWrapper<>();
        // 搜索只展示已上架商品
        wrapper.eq("status", 1);
        if (!normalizedKeyword.isBlank()) {
            wrapper.and(w -> w.like("name", normalizedKeyword).or().like("description", normalizedKeyword));
        }
        wrapper.orderByDesc("created_at");
        Page<PdProduct> page = new Page<>(pageNum, pageSize);
        List<PdProduct> products = productMapper.selectPage(page, wrapper).getRecords();
        cacheProducts(key, products);
        return products;
    }

    private void saveSkus(Long productId, List<PdProductSku> requested, PdProduct product) {
        List<PdProductSku> skus = requested;
        if (skus == null || skus.isEmpty()) {
            PdProductSku sku = new PdProductSku();
            sku.setSkuName("默认规格");
            sku.setPrice(product.getPrice());
            sku.setStock(product.getStock() == null ? 0 : product.getStock());
            sku.setImage(firstImage(product.getImages()));
            skus = List.of(sku);
        }
        boolean first = true;
        Set<String> names = new HashSet<>();
        for (PdProductSku sku : skus) {
            validateSku(sku);
            if (!names.add(sku.getSkuName())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "商品规格名称重复");
            }
            sku.setId(null);
            sku.setProductId(productId);
            sku.setIsDefault(first ? 1 : 0);
            sku.setStatus(1);
            sku.setCreateTime(LocalDateTime.now());
            sku.setUpdateTime(LocalDateTime.now());
            productSkuMapper.insert(sku);
            first = false;
        }
    }

    private void updateSkus(Long productId, List<PdProductSku> requested, PdProduct product) {
        if (requested.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "至少保留一个商品规格");
        }
        QueryWrapper<PdProductSku> wrapper = new QueryWrapper<>();
        wrapper.eq("product_id", productId);
        List<PdProductSku> existing = productSkuMapper.selectList(wrapper);
        Set<Long> requestedIds = new HashSet<>();
        Set<String> names = new HashSet<>();
        boolean first = true;
        for (PdProductSku sku : requested) {
            validateSku(sku);
            if (!names.add(sku.getSkuName())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "商品规格名称重复");
            }
            if (sku.getId() != null && !requestedIds.add(sku.getId())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "商品规格重复");
            }
            sku.setProductId(productId);
            sku.setIsDefault(first ? 1 : 0);
            sku.setStatus(1);
            sku.setUpdateTime(LocalDateTime.now());
            if (sku.getImage() == null || sku.getImage().isBlank()) {
                sku.setImage(firstImage(product.getImages()));
            }
            if (sku.getId() == null) {
                PdProductSku sameName = existing.stream()
                        .filter(e -> e.getSkuName().equals(sku.getSkuName()))
                        .findFirst().orElse(null);
                if (sameName != null) {
                    sku.setId(sameName.getId());
                    sku.setCreateTime(sameName.getCreateTime());
                    productSkuMapper.updateById(sku);
                    requestedIds.add(sku.getId());
                } else {
                    sku.setCreateTime(LocalDateTime.now());
                    productSkuMapper.insert(sku);
                    requestedIds.add(sku.getId());
                }
            } else {
                PdProductSku owned = existing.stream()
                        .filter(e -> e.getId().equals(sku.getId()))
                        .findFirst()
                        .orElseThrow(() -> new BizException(ErrorCode.UNAUTHORIZED, "商品规格不属于该商品"));
                sku.setCreateTime(owned.getCreateTime());
                productSkuMapper.updateById(sku);
            }
            first = false;
        }
        for (PdProductSku old : existing) {
            if (!requestedIds.contains(old.getId())) {
                old.setStatus(0);
                old.setIsDefault(0);
                old.setUpdateTime(LocalDateTime.now());
                productSkuMapper.updateById(old);
            }
        }
    }

    private void validateSku(PdProductSku sku) {
        if (sku.getSkuName() == null || sku.getSkuName().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规格名称不能为空");
        }
        sku.setSkuName(sku.getSkuName().trim());
        if (sku.getPrice() == null || sku.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规格价格必须大于 0");
        }
        if (sku.getStock() == null || sku.getStock() < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "规格库存不能小于 0");
        }
    }

    private void syncProductAggregate(Long productId) {
        QueryWrapper<PdProductSku> wrapper = new QueryWrapper<>();
        wrapper.eq("product_id", productId).eq("status", 1);
        List<PdProductSku> skus = productSkuMapper.selectList(wrapper);
        PdProduct product = productMapper.selectById(productId);
        if (product == null) return;
        if (skus.isEmpty()) {
            product.setPrice(BigDecimal.ZERO);
            product.setStock(0);
        } else {
            product.setPrice(skus.stream().map(PdProductSku::getPrice)
                    .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
            product.setStock(skus.stream().mapToInt(sku ->
                    sku.getStock() == null ? 0 : sku.getStock()).sum());
        }
        product.setUpdateTime(LocalDateTime.now());
        productMapper.updateById(product);
    }

    private String firstImage(String images) {
        if (images == null || images.isBlank()) return null;
        String value = images.trim();
        if (value.startsWith("[")) {
            int start = value.indexOf('"');
            int end = start < 0 ? -1 : value.indexOf('"', start + 1);
            return start >= 0 && end > start ? value.substring(start + 1, end) : null;
        }
        return value.split(",")[0].trim();
    }

    private void cacheProducts(String key, List<PdProduct> products) {
        if (products == null || products.isEmpty()) cache.setNull(key, NULL_TTL);
        else cache.setJson(key, products, PRODUCT_TTL);
    }

    private String productKey(Long id) {
        return "ecommerce:product:id:" + id;
    }

    private void invalidateProductCache() {
        cache.deleteByPattern("ecommerce:product:*");
        cache.delete("ecommerce:seckill:list:active");
        cache.deleteByPattern("ecommerce:seckill:product:*");
    }
}
