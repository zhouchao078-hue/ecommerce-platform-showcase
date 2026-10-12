-- 跨店订单拆单：父订单 + 商家子订单
-- 适用于已经执行过 deploy/sql/init.sql 的现有数据库，只需执行一次。
ALTER TABLE od_order
    ADD COLUMN parent_order_id BIGINT NULL COMMENT '父订单ID：子订单字段，旧订单为空' AFTER id,
    ADD COLUMN merchant_id BIGINT NULL COMMENT '商家ID：商家子订单字段' AFTER user_id,
    ADD COLUMN order_type TINYINT NOT NULL DEFAULT 1
        COMMENT '1=旧独立订单 2=父订单 3=商家子订单' AFTER status,
    ADD INDEX idx_od_order_parent (parent_order_id),
    ADD INDEX idx_od_order_merchant_status (merchant_id, status);
