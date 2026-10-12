-- 售后二期：退货退款
-- 适用于已经执行过 deploy/sql/init.sql 的现有数据库。
-- 该脚本只需执行一次；重复执行会因列已存在而报错。
ALTER TABLE od_after_sale
    ADD COLUMN return_company VARCHAR(50) NULL COMMENT '退货物流公司' AFTER reject_reason,
    ADD COLUMN return_tracking_no VARCHAR(100) NULL COMMENT '退货物流单号' AFTER return_company,
    ADD COLUMN returned_at DATETIME NULL COMMENT '买家寄回时间' AFTER return_tracking_no,
    ADD COLUMN received_at DATETIME NULL COMMENT '商家确认收货时间' AFTER returned_at,
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 0
        COMMENT '0待审核 1已同意 2已拒绝 3已完成 4已撤销 5已寄回待收货';
