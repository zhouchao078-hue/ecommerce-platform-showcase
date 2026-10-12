-- ============================================================
-- Support ticket foundation
-- ============================================================

CREATE TABLE IF NOT EXISTS sp_support_ticket (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    ticket_no         VARCHAR(64)   NOT NULL               COMMENT 'Public ticket number',
    user_id           BIGINT        NOT NULL               COMMENT 'Owner user ID',
    merchant_id       BIGINT        NULL                   COMMENT 'Related merchant ID',
    order_id          BIGINT        NULL                   COMMENT 'Related order ID',
    after_sale_id     BIGINT        NULL                   COMMENT 'Related after-sale ID',
    channel           VARCHAR(32)   NOT NULL DEFAULT 'web' COMMENT 'Source channel',
    client_request_id VARCHAR(64)   NULL                   COMMENT 'Client idempotency key',
    category          VARCHAR(80)   NOT NULL DEFAULT 'general' COMMENT 'Ticket category',
    priority          VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT 'LOW/NORMAL/HIGH/URGENT',
    status            INT           NOT NULL DEFAULT 10    COMMENT 'Ticket status code',
    subject           VARCHAR(200)  NOT NULL               COMMENT 'Ticket subject',
    summary           TEXT          NULL                   COMMENT 'Agent-generated summary',
    assignee_type     VARCHAR(32)   NULL                   COMMENT 'USER/MERCHANT/PLATFORM/HUMAN',
    assignee_id       BIGINT        NULL                   COMMENT 'Assignee ID',
    sla_deadline      DATETIME      NULL                   COMMENT 'SLA deadline',
    last_message_at   DATETIME      NULL                   COMMENT 'Last message time',
    resolved_at       DATETIME      NULL                   COMMENT 'Resolved time',
    closed_at         DATETIME      NULL                   COMMENT 'Closed time',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_support_ticket_no (ticket_no),
    UNIQUE KEY uk_support_ticket_client_request (user_id, client_request_id),
    INDEX idx_support_ticket_user (user_id, status, last_message_at),
    INDEX idx_support_ticket_assignee (assignee_type, assignee_id, status),
    INDEX idx_support_ticket_order (order_id),
    INDEX idx_support_ticket_after_sale (after_sale_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Support ticket';

CREATE TABLE IF NOT EXISTS sp_support_message (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    ticket_id          BIGINT       NOT NULL               COMMENT 'Ticket ID',
    sender_type        VARCHAR(32)  NOT NULL               COMMENT 'USER/AGENT/MERCHANT/HUMAN/SYSTEM',
    sender_id          BIGINT       NULL                   COMMENT 'Sender ID',
    message_type       VARCHAR(32)  NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/JSON/SYSTEM_EVENT',
    content            TEXT         NOT NULL               COMMENT 'Message content',
    structured_payload TEXT         NULL                   COMMENT 'Structured JSON payload',
    client_message_id  VARCHAR(64)  NULL                   COMMENT 'Client idempotency key',
    visibility         VARCHAR(16)  NOT NULL DEFAULT 'EXTERNAL' COMMENT 'EXTERNAL/INTERNAL',
    trace_id           VARCHAR(64)  NULL                   COMMENT 'Agent trace ID',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_support_message_ticket (ticket_id, id),
    INDEX idx_support_message_trace (trace_id),
    UNIQUE KEY uk_support_message_client (ticket_id, client_message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Support ticket message';

CREATE TABLE IF NOT EXISTS sp_support_event (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    ticket_id   BIGINT       NOT NULL               COMMENT 'Ticket ID',
    event_type  VARCHAR(64)  NOT NULL               COMMENT 'Event type',
    from_status INT          NULL                   COMMENT 'Previous status',
    to_status   INT          NULL                   COMMENT 'New status',
    actor_type  VARCHAR(32)  NOT NULL               COMMENT 'Actor type',
    actor_id    BIGINT       NULL                   COMMENT 'Actor ID',
    payload     TEXT         NULL                   COMMENT 'Event payload JSON',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_support_event_ticket (ticket_id, id),
    INDEX idx_support_event_type (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Support ticket event timeline';

CREATE TABLE IF NOT EXISTS sp_support_action (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    action_no             VARCHAR(64)   NOT NULL               COMMENT 'Public action number',
    ticket_id             BIGINT        NOT NULL               COMMENT 'Ticket ID',
    action_type           VARCHAR(64)   NOT NULL               COMMENT 'Action type',
    status                VARCHAR(32)   NOT NULL               COMMENT 'Action status',
    payload               TEXT          NULL                   COMMENT 'Proposed action JSON',
    requires_confirmation TINYINT(1)    NOT NULL DEFAULT 1     COMMENT 'Requires user confirmation',
    idempotency_key       VARCHAR(128)  NOT NULL               COMMENT 'Execution idempotency key',
    executed_at           DATETIME      NULL                   COMMENT 'Execution time',
    result_payload        TEXT          NULL                   COMMENT 'Execution result JSON',
    created_by            BIGINT        NULL                   COMMENT 'Creator user ID',
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_support_action_no (action_no),
    UNIQUE KEY uk_support_action_idempotency (idempotency_key),
    INDEX idx_support_action_ticket (ticket_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Support action proposal and execution';

CREATE TABLE IF NOT EXISTS sp_support_agent_run (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    run_no           VARCHAR(64)  NOT NULL               COMMENT 'Agent run number',
    ticket_id        BIGINT       NOT NULL               COMMENT 'Ticket ID',
    status           VARCHAR(32)  NOT NULL               COMMENT 'Run status',
    provider         VARCHAR(64)  NULL                   COMMENT 'Model provider',
    model            VARCHAR(128) NULL                   COMMENT 'Model name',
    prompt_version   VARCHAR(64)  NULL                   COMMENT 'Prompt version',
    request_payload  LONGTEXT     NULL                   COMMENT 'Request payload JSON',
    response_payload LONGTEXT     NULL                   COMMENT 'Response payload JSON',
    error_message    TEXT         NULL                   COMMENT 'Error detail',
    client_request_id VARCHAR(64) NULL                   COMMENT 'Client idempotency key',
    cancel_requested TINYINT(1)   NOT NULL DEFAULT 0     COMMENT 'Cancellation requested',
    version          INT          NOT NULL DEFAULT 0     COMMENT 'Optimistic version',
    last_completed_step VARCHAR(64) NULL                 COMMENT 'Last completed checkpoint',
    worker_id        VARCHAR(128) NULL                   COMMENT 'Worker owner',
    lease_expires_at DATETIME     NULL                   COMMENT 'Lease expiration',
    last_heartbeat_at DATETIME    NULL                   COMMENT 'Last heartbeat',
    started_at       DATETIME     NULL                   COMMENT 'Start time',
    finished_at      DATETIME     NULL                   COMMENT 'Finish time',
    duration_ms      BIGINT       NULL                   COMMENT 'Duration in milliseconds',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_support_agent_run_no (run_no),
    UNIQUE KEY uk_support_agent_run_client (ticket_id, client_request_id),
    INDEX idx_support_agent_run_ticket (ticket_id, id),
    INDEX idx_support_agent_run_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Support agent execution trace';

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

CREATE TABLE IF NOT EXISTS sp_support_agent_evaluation (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'Primary key',
    run_id       BIGINT       NOT NULL               COMMENT 'Agent run ID',
    score        TINYINT      NOT NULL               COMMENT 'Quality score from 1 to 5',
    tags         VARCHAR(255) NULL                   COMMENT 'Evaluation tags',
    notes        VARCHAR(2000) NULL                  COMMENT 'Review notes',
    evaluator_id BIGINT       NOT NULL               COMMENT 'Admin user ID',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_support_agent_evaluation_run (run_id),
    INDEX idx_support_agent_evaluation_score (score, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Support agent quality evaluation';
