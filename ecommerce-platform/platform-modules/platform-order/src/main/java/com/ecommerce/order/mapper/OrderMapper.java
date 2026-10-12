package com.ecommerce.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ecommerce.order.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 商家维度的订单查询。
     * 新订单直接查商家子订单；旧订单继续用明细 merchant_id 快照兼容。
     */
    @Select("<script>"
            + "SELECT o.* FROM od_order o "
            + "WHERE ("
            + "  (o.parent_order_id IS NOT NULL AND o.merchant_id = #{merchantId}) "
            + "  OR (o.parent_order_id IS NULL AND EXISTS (SELECT 1 FROM od_order_item oi "
            + "      WHERE oi.order_id = o.id AND oi.merchant_id = #{merchantId}))"
            + ") "
            + "<if test='status != null'> AND o.status = #{status} </if>"
            + "ORDER BY o.created_at DESC"
            + "</script>")
    IPage<Order> selectByMerchant(IPage<Order> page,
                                  @Param("merchantId") Long merchantId,
                                  @Param("status") Integer status);
}
