package com.ecommerce.order.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.framework.cache.RedisCacheService;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.mapper.OrderItemMapper;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderPageCache;
import com.ecommerce.order.model.OrderVO;
import com.ecommerce.order.model.OrderSummary;
import com.ecommerce.order.model.SubOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** 订单读缓存。key 始终包含 userId，避免缓存层造成越权读取。 */
@Service
@RequiredArgsConstructor
public class OrderCacheService {
    private static final Duration ORDER_TTL = Duration.ofMinutes(5);
    private static final Duration NULL_TTL = Duration.ofSeconds(30);
    private static final String PREFIX = "ecommerce:order:";

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RedisCacheService cache;

    public IPage<Order> listOrders(Long userId, Integer pageNum, Integer pageSize, Integer status) {
        String key = PREFIX + "user:" + userId + ":page:" + pageNum + ":size:" + pageSize
                + ":status:" + (status == null ? "all" : status);
        OrderPageCache cached = cache.getJson(key, OrderPageCache.class);
        if (cached != null) {
            return toPage(cached);
        }
        if (cache.isNullValue(key)) {
            return new Page<>(pageNum, pageSize, 0);
        }

        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).isNull("parent_order_id");
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        Page<Order> page = orderMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        if (page.getRecords() == null || page.getRecords().isEmpty()) {
            cache.setNull(key, NULL_TTL);
        } else {
            OrderPageCache value = new OrderPageCache();
            value.setCurrent(page.getCurrent());
            value.setSize(page.getSize());
            value.setTotal(page.getTotal());
            value.setRecords(page.getRecords());
            cache.setJson(key, value, ORDER_TTL);
        }
        return page;
    }

    public OrderVO getOrderDetail(Long userId, Long orderId) {
        String key = PREFIX + "user:" + userId + ":detail:" + orderId;
        OrderVO cached = cache.getJson(key, OrderVO.class);
        if (cached != null) {
            return cached;
        }
        if (cache.isNullValue(key)) {
            return null;
        }

        Order order = orderMapper.selectById(orderId);
        if (order == null || !userId.equals(order.getUserId())) {
            cache.setNull(key, NULL_TTL);
            return null;
        }
        OrderVO vo;
        if (Integer.valueOf(Order.TYPE_PARENT).equals(order.getOrderType())) {
            vo = toOrderVO(order, List.of());
            QueryWrapper<Order> childWrapper = new QueryWrapper<>();
            childWrapper.eq("parent_order_id", orderId).orderByAsc("id");
            List<SubOrderVO> subOrders = new ArrayList<>();
            for (Order child : orderMapper.selectList(childWrapper)) {
                QueryWrapper<OrderItem> childItemWrapper = new QueryWrapper<>();
                childItemWrapper.eq("order_id", child.getId());
                subOrders.add(toSubOrderVO(child, orderItemMapper.selectList(childItemWrapper)));
            }
            vo.setSubOrders(subOrders);
        } else {
            QueryWrapper<OrderItem> itemWrapper = new QueryWrapper<>();
            itemWrapper.eq("order_id", orderId);
            vo = toOrderVO(order, orderItemMapper.selectList(itemWrapper));
        }
        cache.setJson(key, vo, ORDER_TTL);
        return vo;
    }

    public Order findByOrderNo(Long userId, String orderNo) {
        String safeOrderNo = orderNo.replace(":", "_");
        String key = PREFIX + "user:" + userId + ":no:" + safeOrderNo;
        Order cached = cache.getJson(key, Order.class);
        if (cached != null) {
            return cached;
        }
        if (cache.isNullValue(key)) {
            return null;
        }
        Order order = orderMapper.selectOne(new QueryWrapper<Order>()
                .eq("user_id", userId)
                .eq("order_no", orderNo)
                .last("LIMIT 1"));
        if (order == null) {
            cache.setNull(key, NULL_TTL);
        } else {
            cache.setJson(key, order, ORDER_TTL);
        }
        return order;
    }

    public List<Order> listRecentOrders(Long userId) {
        return orderMapper.selectList(new QueryWrapper<Order>()
                .eq("user_id", userId).orderByDesc("created_at").last("LIMIT 10"));
    }

    public List<OrderSummary> listRecentSummaries(Long userId) {
        String key = PREFIX + "summary:user:" + userId + ":recent";
        List<OrderSummary> cached = cache.getListJson(key, OrderSummary.class);
        if (cached != null) {
            return cached;
        }
        if (cache.isNullValue(key)) {
            return List.of();
        }
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId).orderByDesc("created_at").last("LIMIT 10");
        List<Order> orders = orderMapper.selectList(wrapper);
        if (orders.isEmpty()) {
            cache.setNull(key, NULL_TTL);
        } else {
            List<OrderSummary> summaries = orders.stream().map(this::toSummary).toList();
            cache.setJson(key, summaries, ORDER_TTL);
            return summaries;
        }
        return List.of();
    }

    public OrderSummary findSummaryByOrderNo(Long userId, String orderNo) {
        String key = PREFIX + "summary:user:" + userId + ":no:" + orderNo.replace(":", "_");
        OrderSummary cached = cache.getJson(key, OrderSummary.class);
        if (cached != null) return cached;
        if (cache.isNullValue(key)) return null;
        Order order = orderMapper.selectOne(new QueryWrapper<Order>()
                .eq("user_id", userId).eq("order_no", orderNo).last("LIMIT 1"));
        if (order == null) {
            cache.setNull(key, NULL_TTL);
            return null;
        }
        OrderSummary summary = toSummary(order);
        cache.setJson(key, summary, ORDER_TTL);
        return summary;
    }

    public void invalidateUser(Long userId) {
        cache.deleteByPattern(PREFIX + "user:" + userId + ":*");
        cache.deleteByPattern(PREFIX + "summary:user:" + userId + ":*");
    }

    private IPage<Order> toPage(OrderPageCache value) {
        Page<Order> page = new Page<>(value.getCurrent(), value.getSize(), value.getTotal());
        page.setRecords(value.getRecords());
        return page;
    }

    private OrderVO toOrderVO(Order order, List<OrderItem> items) {
        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(statusDescription(order.getStatus()));
        vo.setReceiverName(order.getReceiverName());
        vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress());
        vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime());
        vo.setItems(items);
        vo.setParentOrderId(order.getParentOrderId());
        vo.setMerchantId(order.getMerchantId());
        vo.setOrderType(orderTypeName(order.getOrderType()));
        return vo;
    }

    private SubOrderVO toSubOrderVO(Order order, List<OrderItem> items) {
        SubOrderVO vo = new SubOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setMerchantId(order.getMerchantId());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusDesc(statusDescription(order.getStatus()));
        vo.setItems(items);
        return vo;
    }

    private String orderTypeName(Integer orderType) {
        if (Integer.valueOf(Order.TYPE_PARENT).equals(orderType)) return "PARENT";
        if (Integer.valueOf(Order.TYPE_CHILD).equals(orderType)) return "CHILD";
        return "NORMAL";
    }

    private String statusDescription(Integer status) {
        try {
            return OrderStatus.fromCode(status).getDesc();
        } catch (Exception ex) {
            return "UNKNOWN";
        }
    }

    private OrderSummary toSummary(Order order) {
        OrderSummary summary = new OrderSummary();
        summary.setOrderNo(order.getOrderNo());
        summary.setTotalAmount(order.getTotalAmount());
        summary.setStatus(order.getStatus());
        summary.setStatusDesc(statusDescription(order.getStatus()));
        summary.setCreateTime(order.getCreateTime());
        return summary;
    }
}
