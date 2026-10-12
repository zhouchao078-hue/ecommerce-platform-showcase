-- Idempotent migration for outbox publisher leases.

DROP PROCEDURE IF EXISTS add_outbox_column;
DELIMITER //
CREATE PROCEDURE add_outbox_column(
    IN column_name_value VARCHAR(64),
    IN definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'sp_support_outbox'
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `sp_support_outbox` ADD COLUMN `',
            column_name_value,
            '` ',
            definition_value
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

DROP PROCEDURE IF EXISTS add_outbox_index;
DELIMITER //
CREATE PROCEDURE add_outbox_index(
    IN index_name_value VARCHAR(64),
    IN definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'sp_support_outbox'
          AND index_name = index_name_value
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `sp_support_outbox` ADD ',
            definition_value
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL add_outbox_column(
    'claim_token',
    'VARCHAR(64) NULL COMMENT ''Publisher claim token'''
);
CALL add_outbox_column(
    'lease_expires_at',
    'DATETIME NULL COMMENT ''Publisher lease expiration'''
);
CALL add_outbox_index(
    'idx_support_outbox_claim',
    'INDEX `idx_support_outbox_claim` (`status`, `lease_expires_at`, `next_retry_at`, `id`)'
);

DROP PROCEDURE IF EXISTS add_outbox_column;
DROP PROCEDURE IF EXISTS add_outbox_index;
