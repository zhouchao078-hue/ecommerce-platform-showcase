-- 商品多规格 SKU
-- 适用于已经执行过 deploy/sql/init.sql 的现有数据库，可重复执行。

CREATE TABLE IF NOT EXISTS pd_product_sku (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT          NOT NULL,
    sku_name    VARCHAR(200)    NOT NULL,
    spec_json   VARCHAR(500),
    price       DECIMAL(10,2)   NOT NULL,
    stock       INT             NOT NULL DEFAULT 0,
    image       VARCHAR(500),
    is_default  TINYINT         NOT NULL DEFAULT 0,
    status      TINYINT         NOT NULL DEFAULT 1,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_sku_name (product_id, sku_name),
    INDEX idx_product_sku_product (product_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 给每个旧商品补一个默认 SKU
INSERT INTO pd_product_sku (product_id, sku_name, price, stock, image, is_default, status)
SELECT p.id, '默认规格', p.price, COALESCE(p.stock, 0), p.images, 1, 1
FROM pd_product p
WHERE NOT EXISTS (
    SELECT 1 FROM pd_product_sku s WHERE s.product_id = p.id AND s.is_default = 1
);

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS drop_index_if_exists;
DROP PROCEDURE IF EXISTS add_index_if_missing;

DELIMITER $$

CREATE PROCEDURE add_column_if_missing(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN ddl_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = ddl_value;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE drop_index_if_exists(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64)
)
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND index_name = index_name_value
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` DROP INDEX `', index_name_value, '`');
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

CREATE PROCEDURE add_index_if_missing(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN ddl_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND index_name = index_name_value
    ) THEN
        SET @ddl = ddl_value;
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$

DELIMITER ;

CALL add_column_if_missing(
    'cart',
    'sku_id',
    'ALTER TABLE cart ADD COLUMN sku_id BIGINT NULL COMMENT ''SKU ID'' AFTER product_id'
);

UPDATE cart c
JOIN pd_product_sku s ON s.product_id = c.product_id AND s.is_default = 1
SET c.sku_id = s.id
WHERE c.sku_id IS NULL;

CALL drop_index_if_exists('cart', 'uk_cart_user_product');
CALL drop_index_if_exists('cart', 'uk_user_product');
CALL add_index_if_missing(
    'cart',
    'uk_cart_user_sku',
    'ALTER TABLE cart ADD UNIQUE KEY uk_cart_user_sku (user_id, sku_id)'
);

CALL add_column_if_missing(
    'od_order_item',
    'sku_id',
    'ALTER TABLE od_order_item ADD COLUMN sku_id BIGINT NULL COMMENT ''SKU ID（下单时快照）'' AFTER merchant_id'
);
CALL add_column_if_missing(
    'od_order_item',
    'sku_name',
    'ALTER TABLE od_order_item ADD COLUMN sku_name VARCHAR(200) NULL COMMENT ''SKU名称（下单时快照）'' AFTER sku_id'
);

UPDATE od_order_item oi
JOIN pd_product_sku s ON s.product_id = oi.product_id AND s.is_default = 1
SET oi.sku_id = s.id,
    oi.sku_name = s.sku_name
WHERE oi.sku_id IS NULL;

DROP PROCEDURE IF EXISTS add_column_if_missing;
DROP PROCEDURE IF EXISTS drop_index_if_exists;
DROP PROCEDURE IF EXISTS add_index_if_missing;
