package com.ecommerce.order.mapper;

import com.ecommerce.order.model.MerchantDashboardVO;
import com.ecommerce.order.model.MerchantRecentOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MerchantDashboardMapper {

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM pd_product p WHERE p.merchant_id = #{merchantId}) AS productTotal,
              (SELECT COUNT(*) FROM pd_product p WHERE p.merchant_id = #{merchantId} AND p.status = 1) AS productOnSale,
              (
                SELECT COUNT(*) FROM od_order o
                WHERE (
                  (o.parent_order_id IS NOT NULL AND o.merchant_id = #{merchantId})
                  OR (o.parent_order_id IS NULL AND EXISTS (
                    SELECT 1 FROM od_order_item oi
                    WHERE oi.order_id = o.id AND oi.merchant_id = #{merchantId}
                  ))
                )
              ) AS totalOrders,
              (
                SELECT COUNT(*) FROM od_order o
                WHERE o.status = 1 AND (
                  (o.parent_order_id IS NOT NULL AND o.merchant_id = #{merchantId})
                  OR (o.parent_order_id IS NULL AND EXISTS (
                    SELECT 1 FROM od_order_item oi
                    WHERE oi.order_id = o.id AND oi.merchant_id = #{merchantId}
                  ))
                )
              ) AS pendingShip,
              (
                SELECT COUNT(*) FROM od_order o
                WHERE o.status = 2 AND (
                  (o.parent_order_id IS NOT NULL AND o.merchant_id = #{merchantId})
                  OR (o.parent_order_id IS NULL AND EXISTS (
                    SELECT 1 FROM od_order_item oi
                    WHERE oi.order_id = o.id AND oi.merchant_id = #{merchantId}
                  ))
                )
              ) AS pendingReceive,
              (
                SELECT COUNT(*) FROM od_order o
                WHERE o.status = 3 AND (
                  (o.parent_order_id IS NOT NULL AND o.merchant_id = #{merchantId})
                  OR (o.parent_order_id IS NULL AND EXISTS (
                    SELECT 1 FROM od_order_item oi
                    WHERE oi.order_id = o.id AND oi.merchant_id = #{merchantId}
                  ))
                )
              ) AS completedOrders,
              (
                SELECT COUNT(*) FROM od_after_sale a
                WHERE a.merchant_id = #{merchantId} AND a.status = 0
              ) AS pendingAfterSales,
              (
                SELECT COUNT(*) FROM od_after_sale a
                WHERE a.merchant_id = #{merchantId} AND a.status = 5
              ) AS returnWaitingReceipt,
              (
                SELECT COUNT(*) FROM ud_follow f
                WHERE f.target_type = 1 AND f.target_id = #{merchantId}
              ) AS followerCount,
              (
                SELECT COALESCE(SUM(
                  CASE
                    WHEN o.parent_order_id IS NOT NULL THEN o.total_amount
                    ELSE (
                      SELECT COALESCE(SUM(oi.subtotal), 0)
                      FROM od_order_item oi
                      WHERE oi.order_id = o.id AND oi.merchant_id = #{merchantId}
                    )
                  END
                ), 0)
                FROM od_order o
                WHERE o.status IN (1, 2, 3) AND (
                  (o.parent_order_id IS NOT NULL AND o.merchant_id = #{merchantId})
                  OR (o.parent_order_id IS NULL AND EXISTS (
                    SELECT 1 FROM od_order_item oi
                    WHERE oi.order_id = o.id AND oi.merchant_id = #{merchantId}
                  ))
                )
              ) AS totalSales,
              (
                SELECT COALESCE(SUM(
                  CASE
                    WHEN o.parent_order_id IS NOT NULL THEN o.total_amount
                    ELSE (
                      SELECT COALESCE(SUM(oi.subtotal), 0)
                      FROM od_order_item oi
                      WHERE oi.order_id = o.id AND oi.merchant_id = #{merchantId}
                    )
                  END
                ), 0)
                FROM od_order o
                WHERE o.status IN (1, 2, 3) AND o.created_at >= CURRENT_DATE AND (
                  (o.parent_order_id IS NOT NULL AND o.merchant_id = #{merchantId})
                  OR (o.parent_order_id IS NULL AND EXISTS (
                    SELECT 1 FROM od_order_item oi
                    WHERE oi.order_id = o.id AND oi.merchant_id = #{merchantId}
                  ))
                )
              ) AS todaySales
            """)
    MerchantDashboardVO selectSummary(@Param("merchantId") Long merchantId);

    @Select("""
            SELECT
              o.id,
              o.order_no AS orderNo,
              o.receiver_name AS buyerName,
              CASE
                WHEN o.parent_order_id IS NOT NULL THEN o.total_amount
                ELSE (
                  SELECT COALESCE(SUM(oi.subtotal), 0)
                  FROM od_order_item oi
                  WHERE oi.order_id = o.id AND oi.merchant_id = #{merchantId}
                )
              END AS totalAmount,
              o.status,
              o.created_at AS createTime
            FROM od_order o
            WHERE (
              (o.parent_order_id IS NOT NULL AND o.merchant_id = #{merchantId})
              OR (o.parent_order_id IS NULL AND EXISTS (
                SELECT 1 FROM od_order_item oi
                WHERE oi.order_id = o.id AND oi.merchant_id = #{merchantId}
              ))
            )
            ORDER BY o.created_at DESC
            LIMIT 6
            """)
    List<MerchantRecentOrderVO> selectRecentOrders(@Param("merchantId") Long merchantId);
}
