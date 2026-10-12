package com.ecommerce.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ecommerce.cart.mapper.CartMapper;
import com.ecommerce.cart.model.Cart;
import com.ecommerce.common.exception.BizException;
import com.ecommerce.common.exception.ErrorCode;
import com.ecommerce.order.enums.OrderStatus;
import com.ecommerce.order.mapper.OrderItemMapper;
import com.ecommerce.order.mapper.OrderMapper;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderItem;
import com.ecommerce.order.model.OrderVO;
import com.ecommerce.order.model.OrderListItemVO;
import com.ecommerce.order.service.OrderService;
import com.ecommerce.order.service.OrderCacheService;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.model.PdProduct;
import com.ecommerce.product.model.PdProductSku;
import com.ecommerce.product.service.CategoryService;
import com.ecommerce.product.service.ProductService;
import com.ecommerce.product.service.impl.CategoryServiceImpl;
import com.ecommerce.product.seckill.mapper.SeckillOrderMapper;
import com.ecommerce.product.seckill.mapper.SeckillProductMapper;
import com.ecommerce.product.seckill.model.SeckillOrder;
import com.ecommerce.product.seckill.model.SeckillProduct;
import com.ecommerce.user.mapper.UdAddressMapper;
import com.ecommerce.user.model.UdAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartMapper cartMapper;
    private final ProductMapper productMapper;
    private final ProductService productService;
    private final UdAddressMapper udAddressMapper;
    private final CategoryService categoryService;
    private final OrderCacheService orderCacheService;
    private final com.ecommerce.user.service.NotificationService notificationService;
    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillProductMapper seckillProductMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long userId, List<Long> cartItemIds, Long addressId, String remark) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择要结算的商品");
        }
        UdAddress address = udAddressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "地址不存在");
        }
        String orderNo = "ORD" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", new Random().nextInt(1000));
        Map<Long, List<OrderItem>> itemsByMerchant = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Long cartItemId : cartItemIds) {
            QueryWrapper<Cart> cw = new QueryWrapper<>();
            cw.eq("id", cartItemId).last("FOR UPDATE");
            Cart cart = cartMapper.selectOne(cw);
            if (cart == null || !cart.getUserId().equals(userId)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "购物车商品不存在");
            }
            if (cart.getQuantity() == null || cart.getQuantity() <= 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "购物车商品数量异常");
            }
            QueryWrapper<PdProduct> pw = new QueryWrapper<>();
            pw.eq("id", cart.getProductId()).last("FOR UPDATE");
            PdProduct product = productMapper.selectOne(pw);
            if (product == null) throw new BizException(ErrorCode.BAD_REQUEST, "商品不存在");
            if (product.getStatus() == null || product.getStatus() != 1) {
                throw new BizException(ErrorCode.BAD_REQUEST, "商品已下架：" + product.getName());
            }
            PdProductSku sku = resolveCartSku(cart, product);
            BigDecimal subtotal = sku.getPrice().multiply(BigDecimal.valueOf(cart.getQuantity()));
            total = total.add(subtotal);
            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            // 快照商家归属，后续商品被删除也能查到是哪家店的订单
            item.setMerchantId(product.getMerchantId());
            item.setSkuId(sku.getId());
            item.setSkuName(sku.getSkuName());
            item.setProductName(product.getName());
            item.setProductPrice(sku.getPrice());
            item.setProductImage(product.getImages());
            item.setQuantity(cart.getQuantity());
            item.setSubtotal(subtotal);
            itemsByMerchant.computeIfAbsent(product.getMerchantId(), ignored -> new ArrayList<>()).add(item);
            productService.deductSkuStock(sku.getId(), cart.getQuantity());
        }

        LocalDateTime now = LocalDateTime.now();
        Order order = new Order();
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setTotalAmount(total);
        order.setOrderType(Order.TYPE_PARENT);
        order.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
        fillReceiverAndRemark(order, address, remark);
        order.setCreateTime(now);
        order.setUpdateTime(now);
        orderMapper.insert(order);

        int merchantIndex = 1;
        for (Map.Entry<Long, List<OrderItem>> entry : itemsByMerchant.entrySet()) {
            List<OrderItem> merchantItems = entry.getValue();
            BigDecimal merchantTotal = merchantItems.stream()
                    .map(OrderItem::getSubtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Order child = new Order();
            child.setParentOrderId(order.getId());
            child.setUserId(userId);
            child.setMerchantId(entry.getKey());
            child.setOrderNo(orderNo + "-S" + merchantIndex++);
            child.setTotalAmount(merchantTotal);
            child.setOrderType(Order.TYPE_CHILD);
            child.setStatus(OrderStatus.PENDING_PAYMENT.getCode());
            fillReceiverAndRemark(child, address, remark);
            child.setCreateTime(now);
            child.setUpdateTime(now);
            orderMapper.insert(child);

            for (OrderItem item : merchantItems) {
                item.setOrderId(child.getId());
                orderItemMapper.insert(item);
            }
        }

        cartMapper.deleteBatchIds(cartItemIds);
        orderCacheService.invalidateUser(userId);
        return getOrderDetail(order.getId(), userId);
    }

    @Override
    public IPage<Order> listOrders(Long userId, Integer pageNum, Integer pageSize, Integer status) {
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId);
        // 按状态筛选（status 为空表示查询全部订单）
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        return orderCacheService.listOrders(userId, pageNum, pageSize, status);
    }

    @Override
    public OrderVO getOrderDetail(Long id, Long userId) {
        OrderVO cached = orderCacheService.getOrderDetail(userId, id);
        if (cached != null) {
            return cached;
        }
        /*
        Order order = orderMapper.selectById(id);
        if (order == null || !order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        QueryWrapper<OrderItem> iw = new QueryWrapper<>();
        iw.eq("order_id", id);
        List<OrderItem> items = orderItemMapper.selectList(iw);
        OrderVO vo = new OrderVO();
        vo.setId(order.getId()); vo.setOrderNo(order.getOrderNo()); vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus()); vo.setStatusDesc(OrderStatus.fromCode(order.getStatus()).getDesc());
        vo.setReceiverName(order.getReceiverName()); vo.setReceiverPhone(order.getReceiverPhone());
        vo.setReceiverAddress(order.getReceiverAddress()); vo.setRemark(order.getRemark());
        vo.setCreateTime(order.getCreateTime()); vo.setItems(items);
        return vo;
        */
        throw new BizException(ErrorCode.NOT_FOUND, "璁㈠崟涓嶅瓨鍦?");
    }

    @Override
    public OrderVO getSeckillOrderDetail(String orderNo, Long userId) {
        SeckillOrder seckillOrder = seckillOrderMapper.selectOne(new QueryWrapper<SeckillOrder>()
                .eq("order_no", orderNo)
                .eq("user_id", userId)
                .last("LIMIT 1"));
        if (seckillOrder == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }

        SeckillProduct event = seckillProductMapper.selectById(seckillOrder.getSeckillProductId());
        PdProduct product = event == null ? null : productMapper.selectById(event.getProductId());
        if (product == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单商品不存在");
        }

        OrderItem item = new OrderItem();
        item.setId(seckillOrder.getId());
        item.setProductId(product.getId());
        item.setProductName(product.getName());
        item.setProductPrice(seckillOrder.getPrice());
        item.setProductImage(product.getImages());
        item.setQuantity(1);
        item.setSubtotal(seckillOrder.getPrice());

        OrderVO vo = new OrderVO();
        vo.setId(seckillOrder.getId());
        vo.setOrderNo(seckillOrder.getOrderNo());
        vo.setTotalAmount(seckillOrder.getPrice());
        vo.setStatus(seckillOrder.getStatus());
        try {
            vo.setStatusDesc(OrderStatus.fromCode(seckillOrder.getStatus()).getDesc());
        } catch (Exception ignored) {
            vo.setStatusDesc("处理中");
        }
        vo.setRemark("秒杀活动订单");
        vo.setCreateTime(seckillOrder.getCreatedAt());
        vo.setItems(List.of(item));
        vo.setOrderType("SECKILL");
        return vo;
    }

    @Override
    public IPage<OrderListItemVO> listAllOrders(Long userId, Integer pageNum, Integer pageSize, Integer status) {
        int current = Math.max(1, pageNum == null ? 1 : pageNum);
        int size = Math.max(1, Math.min(100, pageSize == null ? 10 : pageSize));
        List<OrderListItemVO> combined = new ArrayList<>();

        QueryWrapper<Order> normalQuery = new QueryWrapper<>();
        normalQuery.eq("user_id", userId).isNull("parent_order_id");
        if (status != null) normalQuery.eq("status", status);
        orderMapper.selectList(normalQuery).forEach(order -> {
            OrderListItemVO item = new OrderListItemVO();
            item.setId(order.getId());
            item.setOrderNo(order.getOrderNo());
            item.setTotalAmount(order.getTotalAmount());
            item.setStatus(order.getStatus());
            item.setReceiverName(order.getReceiverName());
            item.setReceiverPhone(order.getReceiverPhone());
            item.setCreateTime(order.getCreateTime());
            boolean parentOrder = Integer.valueOf(Order.TYPE_PARENT).equals(order.getOrderType());
            item.setOrderType(parentOrder ? "PARENT" : "NORMAL");
            if (parentOrder) {
                QueryWrapper<Order> childQuery = new QueryWrapper<>();
                childQuery.eq("parent_order_id", order.getId());
                item.setSubOrderCount(Math.toIntExact(orderMapper.selectCount(childQuery)));
            }
            combined.add(item);
        });

        QueryWrapper<SeckillOrder> seckillQuery = new QueryWrapper<>();
        seckillQuery.eq("user_id", userId);
        if (status != null) seckillQuery.eq("status", status);
        seckillOrderMapper.selectList(seckillQuery).forEach(order -> {
            OrderListItemVO item = new OrderListItemVO();
            item.setId(order.getId());
            item.setOrderNo(order.getOrderNo());
            item.setTotalAmount(order.getPrice());
            item.setStatus(order.getStatus());
            item.setCreateTime(order.getCreatedAt());
            item.setOrderType("SECKILL");
            SeckillProduct event = seckillProductMapper.selectById(order.getSeckillProductId());
            if (event != null) {
                PdProduct product = productMapper.selectById(event.getProductId());
                if (product != null) item.setProductName(product.getName());
            }
            combined.add(item);
        });

        combined.sort(Comparator.comparing(OrderListItemVO::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        long total = combined.size();
        long from = Math.min((long) (current - 1) * size, total);
        long to = Math.min(from + size, total);
        Page<OrderListItemVO> page = new Page<>(current, size, total);
        page.setRecords(new ArrayList<>(combined.subList((int) from, (int) to)));
        return page;
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id, Long userId) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权操作此订单");
        }
        if (order.getParentOrderId() != null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请取消父订单，不能单独取消商家子订单");
        }
        if (!order.getStatus().equals(OrderStatus.PENDING_PAYMENT.getCode())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "只能取消待付款的订单");
        }
        // 校验通过，执行取消（回补库存 + 置为已取消）
        doCancel(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelTimeoutOrder(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return; // 订单不存在则忽略（幂等）
        }
        // 只有待付款的订单才取消（幂等：已处理过的自然跳过）
        if (!order.getStatus().equals(OrderStatus.PENDING_PAYMENT.getCode())) {
            return;
        }
        doCancel(order);
    }

    /** 取消订单的公共逻辑：回补商品库存 + 订单置为已取消（供手动取消与超时任务复用） */
    private void doCancel(Order order) {
        // 父订单取消时，需要回补所有商家子订单的库存
        QueryWrapper<Order> childQuery = new QueryWrapper<>();
        childQuery.eq("parent_order_id", order.getId());
        List<Order> children = orderMapper.selectList(childQuery);
        if (children.isEmpty()) {
            restoreOrderStock(order.getId());
        } else {
            for (Order child : children) {
                restoreOrderStock(child.getId());
                child.setStatus(OrderStatus.CANCELLED.getCode());
                child.setUpdateTime(LocalDateTime.now());
                orderMapper.updateById(child);
            }
        }

        // 2. 更新订单状态为已取消
        order.setStatus(OrderStatus.CANCELLED.getCode());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(order);
        orderCacheService.invalidateUser(order.getUserId());
    }

    private void restoreOrderStock(Long orderId) {
        QueryWrapper<OrderItem> iw = new QueryWrapper<>();
        iw.eq("order_id", orderId);
        List<OrderItem> items = orderItemMapper.selectList(iw);
        for (OrderItem item : items) {
            if (item.getSkuId() != null) {
                productService.restoreSkuStock(item.getSkuId(), item.getQuantity());
            } else {
                productService.restoreStock(item.getProductId(), item.getQuantity());
            }
        }
    }

    private PdProductSku resolveCartSku(com.ecommerce.cart.model.Cart cart, PdProduct product) {
        if (cart.getSkuId() != null) {
            return productService.getSku(cart.getSkuId());
        }
        List<PdProductSku> skus = productService.listActiveSkus(product.getId());
        if (skus.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "商品没有可用规格");
        }
        return skus.get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long id, Long userId) {
        Order order = orderMapper.selectById(id);
        if(id ==  null || order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "不存在此订单");
        }
        if(!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "无权修改此订单");
        }
        if (order.getParentOrderId() != null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请对父订单发起支付");
        }

        if(!order.getStatus().equals(OrderStatus.PENDING_PAYMENT.getCode())) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }else {
            order.setStatus(OrderStatus.PENDING_SHIP.getCode());
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateById(order);

            QueryWrapper<Order> childQuery = new QueryWrapper<>();
            childQuery.eq("parent_order_id", order.getId());
            List<Order> children = orderMapper.selectList(childQuery);
            for (Order child : children) {
                child.setStatus(OrderStatus.PENDING_SHIP.getCode());
                child.setUpdateTime(LocalDateTime.now());
                orderMapper.updateById(child);
            }
            orderCacheService.invalidateUser(order.getUserId());
        }

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(Long id, Long userId) {
        Order order = orderMapper.selectById(id);
        if (id == null || order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "不存在此订单");
        }
        // 注意：这里是「商家发货」，调用方必须是商家而不是买家。
        // 之前这里写的是 order.getUserId().equals(userId)（买家校验），
        // 只有「商家恰好也是买家」时才通过，真实场景下商家发货会被误拒。
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        if (order.getParentOrderId() != null) {
            // 新拆单模型：一张商家子订单只属于一个商家
            if (!merchantId.equals(order.getMerchantId())) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "此订单不属于你的店铺");
            }
        } else {
            // 兼容旧订单：旧订单没有商家快照，只能逐条明细校验
            LambdaQueryWrapper<OrderItem> iw = new LambdaQueryWrapper<>();
            iw.eq(OrderItem::getOrderId, order.getId());
            List<OrderItem> items = orderItemMapper.selectList(iw);
            for (OrderItem item : items) {
                if (!merchantId.equals(item.getMerchantId())) {
                    throw new BizException(ErrorCode.UNAUTHORIZED, "此订单不属于你的店铺");
                }
            }
        }

        if (!order.getStatus().equals(OrderStatus.PENDING_SHIP.getCode())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "订单未付款，不能发货");
            } else {
                order.setStatus(OrderStatus.SHIPPED.getCode());
                order.setUpdateTime(LocalDateTime.now());
                orderMapper.updateById(order);
                refreshParentStatus(order.getParentOrderId());
                orderCacheService.invalidateUser(order.getUserId());

                // 通知买家：独立事务 + 吞异常，不会把发货事务带崩
                notificationService.send(
                        order.getUserId(),
                        com.ecommerce.user.model.Notification.TYPE_ORDER,
                        "你的订单已发货",
                        String.format("订单 %s 已由商家发出，请注意查收。", order.getOrderNo()),
                        com.ecommerce.user.model.Notification.REF_ORDER,
                        order.getId(),
                        ""   // 一次性事件：同一订单只发一条发货通知
                );
            }
        }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IPage<Order> listMerchantOrders(Long userId, Integer pageNum, Integer pageSize, Integer status) {
        // getMerchantIdByUserId 内部已做「不是商家就抛错」的校验
        Long merchantId = categoryService.getMerchantIdByUserId(userId);
        Page<Order> page = new Page<>(pageNum == null ? 1 : pageNum,
                pageSize == null ? 10 : pageSize);
        return orderMapper.selectByMerchant(page, merchantId, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void conFirmOrder(Long id, Long userId){
            Order order = orderMapper.selectById(id);
            if (id == null || order == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "不存在此订单");
            }
            if (!order.getUserId().equals(userId)) {
                throw new BizException(ErrorCode.UNAUTHORIZED, "无权修改此订单");
            }
            if (!order.getStatus().equals(OrderStatus.SHIPPED.getCode())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "订单未发货，不能确认收货");
            } else {
                order.setStatus(OrderStatus.COMPLETED.getCode());
                order.setUpdateTime(LocalDateTime.now());
                orderMapper.updateById(order);
                refreshParentStatus(order.getParentOrderId());
                orderCacheService.invalidateUser(order.getUserId());
            }
        }

    private void fillReceiverAndRemark(Order order, UdAddress address, String remark) {
        order.setReceiverName(address.getReceiverName());
        order.setReceiverPhone(address.getPhone());
        String addr = (address.getProvince() != null ? address.getProvince() : "")
                + (address.getCity() != null ? address.getCity() : "")
                + (address.getDistrict() != null ? address.getDistrict() : "")
                + address.getDetailAddress();
        order.setReceiverAddress(addr);
        order.setRemark(remark);
    }

    /**
     * 父订单状态由子订单汇总：
     * 全部完成 -> 已完成；全部发货 -> 待收货；否则至少已付款 -> 待发货。
     */
    private void refreshParentStatus(Long parentOrderId) {
        if (parentOrderId == null) return;
        Order parent = orderMapper.selectById(parentOrderId);
        if (parent == null || Integer.valueOf(OrderStatus.CANCELLED.getCode()).equals(parent.getStatus())) {
            return;
        }
        QueryWrapper<Order> childQuery = new QueryWrapper<>();
        childQuery.eq("parent_order_id", parentOrderId);
        List<Order> children = orderMapper.selectList(childQuery);
        if (children.isEmpty()) return;

        boolean allCompleted = children.stream()
                .allMatch(child -> Integer.valueOf(OrderStatus.COMPLETED.getCode()).equals(child.getStatus()));
        boolean allShipped = children.stream()
                .allMatch(child -> Integer.valueOf(OrderStatus.SHIPPED.getCode()).equals(child.getStatus())
                        || Integer.valueOf(OrderStatus.COMPLETED.getCode()).equals(child.getStatus()));
        int status = allCompleted
                ? OrderStatus.COMPLETED.getCode()
                : (allShipped ? OrderStatus.SHIPPED.getCode() : OrderStatus.PENDING_SHIP.getCode());
        parent.setStatus(status);
        parent.setUpdateTime(LocalDateTime.now());
        orderMapper.updateById(parent);
    }
}
