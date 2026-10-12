-- Idempotent migration for existing support ticket tables.

DROP PROCEDURE IF EXISTS add_support_column;
DELIMITER //
CREATE PROCEDURE add_support_column(
    IN table_name_value VARCHAR(64),
    IN column_name_value VARCHAR(64),
    IN definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND column_name = column_name_value
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `', table_name_value, '` ADD COLUMN `',
            column_name_value, '` ', definition_value
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

DROP PROCEDURE IF EXISTS add_support_index;
DELIMITER //
CREATE PROCEDURE add_support_index(
    IN table_name_value VARCHAR(64),
    IN index_name_value VARCHAR(64),
    IN definition_value TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = table_name_value
          AND index_name = index_name_value
    ) THEN
        SET @ddl = CONCAT(
            'ALTER TABLE `', table_name_value, '` ADD ',
            definition_value
        );
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END//
DELIMITER ;

CALL add_support_column(
    'sp_support_message',
    'client_message_id',
    'VARCHAR(64) NULL COMMENT ''Client idempotency key'''
);
CALL add_support_index(
    'sp_support_message',
    'uk_support_message_client',
    'UNIQUE KEY `uk_support_message_client` (`ticket_id`, `client_message_id`)'
);

CALL add_support_column(
    'sp_support_agent_run',
    'client_request_id',
    'VARCHAR(64) NULL COMMENT ''Client idempotency key'''
);
CALL add_support_column(
    'sp_support_agent_run',
    'cancel_requested',
    'TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''Cancellation requested'''
);
CALL add_support_column(
    'sp_support_agent_run',
    'version',
    'INT NOT NULL DEFAULT 0 COMMENT ''Optimistic version'''
);
CALL add_support_column(
    'sp_support_agent_run',
    'last_completed_step',
    'VARCHAR(64) NULL COMMENT ''Last completed checkpoint'''
);
CALL add_support_column(
    'sp_support_agent_run',
    'worker_id',
    'VARCHAR(128) NULL COMMENT ''Worker owner'''
);
CALL add_support_column(
    'sp_support_agent_run',
    'lease_expires_at',
    'DATETIME NULL COMMENT ''Lease expiration'''
);
CALL add_support_column(
    'sp_support_agent_run',
    'last_heartbeat_at',
    'DATETIME NULL COMMENT ''Last heartbeat'''
);
CALL add_support_index(
    'sp_support_agent_run',
    'uk_support_agent_run_client',
    'UNIQUE KEY `uk_support_agent_run_client` (`ticket_id`, `client_request_id`)'
);

CREATE TABLE IF NOT EXISTS sp_support_agent_run_step (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    run_id         BIGINT       NOT NULL               COMMENT 'Agent run ID',
    step_name      VARCHAR(64)  NOT NULL               COMMENT 'Checkpoint step name',
    status         VARCHAR(32)  NOT NULL               COMMENT 'RUNNING/SUCCEEDED/FAILED/SKIPPED',
    input_hash     VARCHAR(128) NULL                   COMMENT 'Input hash',
    output_payload LONGTEXT     NULL                   COMMENT 'Structured output JSON',
    error_message  TEXT         NULL                   COMMENT 'Error detail',
    attempt        INT          NOT NULL DEFAULT 1     COMMENT 'Attempt number',
    started_at     DATETIME     NULL                   COMMENT 'Start time',
    finished_at    DATETIME     NULL                   COMMENT 'Finish time',
    duration_ms    BIGINT       NULL                   COMMENT 'Duration in milliseconds',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_support_agent_step_run (run_id, id),
    INDEX idx_support_agent_step_name (run_id, step_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Support agent step checkpoint';

CREATE TABLE IF NOT EXISTS sp_support_agent_run_event (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    run_id              BIGINT       NOT NULL               COMMENT 'Agent run ID',
    event_type          VARCHAR(64)  NOT NULL               COMMENT 'Lifecycle event type',
    status              VARCHAR(32)  NULL                   COMMENT 'Run status at event time',
    last_completed_step VARCHAR(64)  NULL                   COMMENT 'Last completed step',
    worker_id           VARCHAR(128) NULL                   COMMENT 'Worker owner',
    lease_expires_at    DATETIME     NULL                   COMMENT 'Lease expiration',
    last_heartbeat_at   DATETIME     NULL                   COMMENT 'Last heartbeat',
    error_message       TEXT         NULL                   COMMENT 'Error detail',
    payload             LONGTEXT     NULL                   COMMENT 'Structured event payload',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_support_agent_event_run (run_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Support agent run event';

CREATE TABLE IF NOT EXISTS sp_support_outbox (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    aggregate_type VARCHAR(64)  NOT NULL               COMMENT 'Aggregate type',
    aggregate_id   BIGINT       NOT NULL               COMMENT 'Aggregate ID',
    event_type     VARCHAR(64)  NOT NULL               COMMENT 'Event type',
    transport      VARCHAR(16)  NOT NULL DEFAULT 'RABBIT' COMMENT 'RABBIT/KAFKA',
    destination    VARCHAR(128) NULL                   COMMENT 'Exchange or Kafka topic',
    payload        LONGTEXT     NOT NULL               COMMENT 'Event payload JSON',
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/SENT/FAILED',
    retry_count    INT          NOT NULL DEFAULT 0     COMMENT 'Retry count',
    next_retry_at  DATETIME     NOT NULL               COMMENT 'Next retry time',
    claim_token    VARCHAR(64)  NULL                   COMMENT 'Publisher claim token',
    lease_expires_at DATETIME   NULL                   COMMENT 'Publisher lease expiration',
    last_error     TEXT         NULL                   COMMENT 'Last error',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_support_outbox_pending (status, next_retry_at, id),
    INDEX idx_support_outbox_claim (status, lease_expires_at, next_retry_at, id),
    INDEX idx_support_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Support outbox';

CALL add_support_column(
    'sp_support_outbox',
    'transport',
    'VARCHAR(16) NOT NULL DEFAULT ''RABBIT'' COMMENT ''RABBIT/KAFKA'''
);
CALL add_support_column(
    'sp_support_outbox',
    'destination',
    'VARCHAR(128) NULL COMMENT ''Exchange or Kafka topic'''
);
CALL add_support_column(
    'sp_support_outbox',
    'claim_token',
    'VARCHAR(64) NULL COMMENT ''Publisher claim token'''
);
CALL add_support_column(
    'sp_support_outbox',
    'lease_expires_at',
    'DATETIME NULL COMMENT ''Publisher lease expiration'''
);
CALL add_support_index(
    'sp_support_outbox',
    'idx_support_outbox_claim',
    'INDEX `idx_support_outbox_claim` (`status`, `lease_expires_at`, `next_retry_at`, `id`)'
);

DROP PROCEDURE IF EXISTS add_support_column;
DROP PROCEDURE IF EXISTS add_support_index;
