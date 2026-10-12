package com.ecommerce.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.order.model.Order;
import com.ecommerce.order.model.OrderVO;
import com.ecommerce.order.model.OrderListItemVO;
import java.util.List;

public interface OrderService {
    /**
     * 从购物车创建订单
     * @param userId 用户ID
     * @param cartItemIds 选中的购物车记录ID列表
     * @param addressId 收货地址ID
     * @param remark 订单备注（可选）
     * @return 订单详情（含商品明细快照）
     */
    OrderVO createOrder(Long userId, List<Long> cartItemIds, Long addressId, String remark);

    /**
     * 查询当前用户的订单列表（分页）
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    IPage<Order> listOrders(Long userId, Integer pageNum, Integer pageSize, Integer status);

    IPage<OrderListItemVO> listAllOrders(Long userId, Integer pageNum, Integer pageSize, Integer status);

    /**
     * 查询订单详情（含商品明细）
     * @param id 订单ID
     * @param userId 用户ID（用于权限校验）
     * @return 订单详情
     */
    OrderVO getOrderDetail(Long id, Long userId);

    OrderVO getSeckillOrderDetail(String orderNo, Long userId);

    /** 取消订单（仅待付款状态可取消） */
    void cancelOrder(Long id, Long userId);
    /**
     * 定时任务用：取消超时未支付的订单（仅状态校验，无归属校验）
     * @param orderId 订单ID
     */
    void cancelTimeoutOrder(Long orderId);
    /** 订单支付*/
    void payOrder(Long id, Long userId);
    /** 订单发货*/
    void shipOrder(Long id, Long userId);

    /**
     * 商家维度：我店铺里的订单。
     * 用订单明细的 merchant_id 快照关联，商品被删除也能查到。
     */
    IPage<Order> listMerchantOrders(Long userId, Integer pageNum, Integer pageSize, Integer status);
    /** 确认收款*/
    void conFirmOrder(Long id, Long userId);
}
