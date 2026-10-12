package com.ecommerce.product.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.framework.cache.RedisCacheService;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.model.PdProduct;
import com.ecommerce.product.seckill.mapper.SeckillOrderMapper;
import com.ecommerce.product.seckill.mapper.SeckillProductMapper;
import com.ecommerce.product.seckill.model.SeckillOrder;
import com.ecommerce.product.seckill.model.SeckillProduct;
import com.ecommerce.product.seckill.model.SeckillProductCreateRequest;
import com.ecommerce.product.seckill.model.SeckillProductVO;
import com.ecommerce.product.seckill.model.SeckillPurchaseVO;
import com.ecommerce.product.seckill.service.SeckillProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeckillProductServiceImpl implements SeckillProductService {
    private static final String LIST_KEY = "ecommerce:seckill:list:active";
    private static final String PRODUCT_KEY_PREFIX = "ecommerce:seckill:product:";
    private static final String STOCK_KEY_PREFIX = "ecommerce:seckill:stock:";
    private static final String PURCHASE_KEY_PREFIX = "ecommerce:seckill:order:user:";
    private static final Duration LIST_TTL = Duration.ofSeconds(10);
    private static final Duration PRODUCT_TTL = Duration.ofMinutes(2);
    private static final Duration NULL_TTL = Duration.ofSeconds(15);

    private static final DefaultRedisScript<Long> PURCHASE_SCRIPT = new DefaultRedisScript<>("""
            local stock = redis.call('GET', KEYS[1])
            if not stock then return -1 end
            if redis.call('EXISTS', KEYS[2]) == 1 then return -2 end
            if tonumber(stock) <= 0 then return 0 end
            redis.call('DECR', KEYS[1])
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
            return 1
            """, Long.class);

    private final SeckillProductMapper seckillProductMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final ProductMapper productMapper;
    private final RedisCacheService cache;

    @Override
    public List<SeckillProductVO> listAvailable() {
        List<SeckillProductVO> cached = cache.getListJson(LIST_KEY, SeckillProductVO.class);
        if (cached != null) {
            return hydrateStock(cached);
        }
        if (cache.isNullValue(LIST_KEY)) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<SeckillProduct> events = seckillProductMapper.selectList(new QueryWrapper<SeckillProduct>()
                .eq("status", 1)
                .ge("end_time", now)
                .orderByAsc("start_time"));
        List<SeckillProductVO> result = events.stream()
                .map(this::toVO)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (result.isEmpty()) {
            cache.setNull(LIST_KEY, NULL_TTL);
        } else {
            cache.setJson(LIST_KEY, result, LIST_TTL);
        }
        return hydrateStock(result);
    }

    @Override
    public SeckillProductVO getAvailable(Long id) {
        SeckillProduct event = getEvent(id);
        if (event == null || event.getStatus() == null || event.getStatus() != 1
                || event.getEndTime() == null || event.getEndTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        SeckillProductVO vo = toVO(event);
        if (vo != null) {
            hydrateStock(List.of(vo));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(Long merchantId, SeckillProductCreateRequest request) {
        validateCreateRequest(request);
        PdProduct product = productMapper.selectById(request.getProductId());
        if (product == null || !merchantId.equals(product.getMerchantId())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权为此商品创建秒杀活动");
        }
        SeckillProduct event = new SeckillProduct();
        event.setProductId(request.getProductId());
        event.setSeckillPrice(request.getSeckillPrice());
        event.setStock(request.getStock());
        event.setStartTime(request.getStartTime());
        event.setEndTime(request.getEndTime());
        event.setStatus(1);
        event.setVersion(0);
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        seckillProductMapper.insert(event);
        cache.delete(LIST_KEY);
        cache.setJson(PRODUCT_KEY_PREFIX + event.getId(), event, PRODUCT_TTL);
        cache.set(STOCK_KEY_PREFIX + event.getId(), String.valueOf(event.getStock()), PRODUCT_TTL.plusHours(2));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillPurchaseVO purchase(Long seckillProductId, Long userId) {
        SeckillOrder existingOrder = findExistingOrder(seckillProductId, userId);
        if (existingOrder != null) {
            return new SeckillPurchaseVO(existingOrder.getOrderNo());
        }
        SeckillProduct event = getEvent(seckillProductId);
        if (event == null || event.getStatus() == null || event.getStatus() != 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "秒杀活动不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(event.getStartTime())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "秒杀活动尚未开始");
        }
        if (!now.isBefore(event.getEndTime())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "秒杀活动已结束");
        }
        ensureStockCache(event);

        String orderNo = "SK" + UUID.randomUUID().toString().replace("-", "");
        String stockKey = STOCK_KEY_PREFIX + seckillProductId;
        String purchaseKey = PURCHASE_KEY_PREFIX + userId + ":product:" + seckillProductId;
        long ttlSeconds = Math.max(1, Duration.between(now, event.getEndTime()).getSeconds());
        Long result = cache.execute(PURCHASE_SCRIPT, List.of(stockKey, purchaseKey), orderNo,
                String.valueOf(ttlSeconds));
        if (result == null || result == -1) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "秒杀服务暂不可用");
        }
        if (result == -2) {
            SeckillOrder concurrentOrder = findExistingOrder(seckillProductId, userId);
            if (concurrentOrder != null) {
                return new SeckillPurchaseVO(concurrentOrder.getOrderNo());
            }
            throw new BizException(ErrorCode.BAD_REQUEST, "每个用户限购一件");
        }
        if (result == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "秒杀库存不足");
        }

        try {
            if (seckillProductMapper.decrementStock(seckillProductId) != 1) {
                cache.delete(purchaseKey);
                throw new BizException(ErrorCode.BAD_REQUEST, "秒杀库存不足");
            }
            SeckillOrder order = new SeckillOrder();
            order.setOrderNo(orderNo);
            order.setUserId(userId);
            order.setSeckillProductId(seckillProductId);
            order.setPrice(event.getSeckillPrice());
            order.setStatus(0);
            order.setCreatedAt(LocalDateTime.now());
            seckillOrderMapper.insert(order);
            return new SeckillPurchaseVO(orderNo);
        } catch (DuplicateKeyException ex) {
            cache.increment(stockKey, 1);
            SeckillOrder concurrentOrder = findExistingOrder(seckillProductId, userId);
            if (concurrentOrder != null) {
                return new SeckillPurchaseVO(concurrentOrder.getOrderNo());
            }
            throw new BizException(ErrorCode.BAD_REQUEST, "每个用户限购一件");
        } catch (RuntimeException ex) {
            cache.increment(stockKey, 1);
            cache.delete(purchaseKey);
            throw ex;
        }
    }

    private SeckillOrder findExistingOrder(Long seckillProductId, Long userId) {
        return seckillOrderMapper.selectOne(new QueryWrapper<SeckillOrder>()
                .eq("user_id", userId)
                .eq("seckill_product_id", seckillProductId)
                .last("LIMIT 1"));
    }

    private SeckillProduct getEvent(Long id) {
        String key = PRODUCT_KEY_PREFIX + id;
        SeckillProduct cached = cache.getJson(key, SeckillProduct.class);
        if (cached != null) {
            return cached;
        }
        if (cache.isNullValue(key)) {
            return null;
        }
        SeckillProduct event = seckillProductMapper.selectById(id);
        if (event == null) {
            cache.setNull(key, NULL_TTL);
        } else {
            cache.setJson(key, event, PRODUCT_TTL);
        }
        return event;
    }

    private void ensureStockCache(SeckillProduct event) {
        String key = STOCK_KEY_PREFIX + event.getId();
        if (!cache.exists(key)) {
            SeckillProduct current = seckillProductMapper.selectById(event.getId());
            if (current != null) {
                long seconds = Math.max(1, Duration.between(LocalDateTime.now(), current.getEndTime()).getSeconds());
                cache.set(key, String.valueOf(current.getStock()), Duration.ofSeconds(seconds));
            }
        }
    }

    private List<SeckillProductVO> hydrateStock(List<SeckillProductVO> products) {
        products.forEach(product -> {
            String stock = cache.get(STOCK_KEY_PREFIX + product.getId());
            if (stock != null && !RedisCacheService.NULL_VALUE.equals(stock)) {
                product.setStock(Integer.valueOf(stock));
            }
        });
        return products;
    }

    private SeckillProductVO toVO(SeckillProduct event) {
        PdProduct product = productMapper.selectById(event.getProductId());
        if (product == null) {
            return null;
        }
        SeckillProductVO vo = new SeckillProductVO();
        vo.setId(event.getId());
        vo.setProductId(event.getProductId());
        vo.setName(product.getName());
        vo.setDescription(product.getDescription());
        vo.setImages(product.getImages());
        vo.setOriginalPrice(product.getPrice());
        vo.setSeckillPrice(event.getSeckillPrice());
        vo.setStock(event.getStock());
        vo.setStartTime(event.getStartTime());
        vo.setEndTime(event.getEndTime());
        return vo;
    }

    private void validateCreateRequest(SeckillProductCreateRequest request) {
        if (request == null || request.getProductId() == null || request.getSeckillPrice() == null
                || request.getStock() == null || request.getStock() <= 0
                || request.getStartTime() == null || request.getEndTime() == null
                || !request.getEndTime().isAfter(request.getStartTime())
                || request.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "秒杀商品参数不完整或时间、价格、库存不合法");
        }
    }
}
