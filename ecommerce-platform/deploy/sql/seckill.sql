-- Existing installations can run this migration once.
CREATE TABLE IF NOT EXISTS seckill_product (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id      BIGINT          NOT NULL,
    seckill_price   DECIMAL(10,2)   NOT NULL,
    stock           INT             NOT NULL DEFAULT 0,
    start_time      DATETIME        NOT NULL,
    end_time        DATETIME        NOT NULL,
    status          TINYINT         NOT NULL DEFAULT 1,
    version         INT             NOT NULL DEFAULT 0,
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_seckill_product_product_id (product_id),
    INDEX idx_seckill_product_time (start_time, end_time, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS seckill_order (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no            VARCHAR(64)     NOT NULL,
    user_id             BIGINT          NOT NULL,
    seckill_product_id  BIGINT          NOT NULL,
    price               DECIMAL(10,2)   NOT NULL,
    status              TINYINT         NOT NULL DEFAULT 0,
    created_at          DATETIME        DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_seckill_order_no (order_no),
    UNIQUE KEY uk_seckill_user_product (user_id, seckill_product_id),
    INDEX idx_seckill_order_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
