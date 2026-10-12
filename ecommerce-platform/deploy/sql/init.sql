-- ============================================================
-- 电商平台初始化脚本（MySQL）
-- 创建数据库和基础表结构
-- ============================================================

-- 如果手动执行，请先创建数据库：
CREATE DATABASE IF NOT EXISTS ecommerce DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ecommerce;

-- ============================================================
-- 系统用户表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username        VARCHAR(32)     NOT NULL               COMMENT '用户名',
    password_hash   VARCHAR(255)    NOT NULL               COMMENT '密码哈希值（BCrypt）',
    phone           VARCHAR(20)                             COMMENT '手机号',
    email           VARCHAR(100)                            COMMENT '邮箱',
    avatar          VARCHAR(500)                            COMMENT '头像URL',
    status          TINYINT         DEFAULT 1               COMMENT '状态：1=启用 0=禁用',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_sys_user_username (username),
    UNIQUE INDEX uk_sys_user_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ============================================================
-- 系统角色表
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name            VARCHAR(32)     NOT NULL               COMMENT '角色名称',
    code            VARCHAR(32)     NOT NULL               COMMENT '角色编码（ROLE_USER/ROLE_MERCHANT/ROLE_ADMIN）',
    description     VARCHAR(200)                            COMMENT '角色描述',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE INDEX uk_sys_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- ============================================================
-- 用户-角色关联表（多对多）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id         BIGINT          NOT NULL COMMENT '用户ID',
    role_id         BIGINT          NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

-- ============================================================
-- 用户地址表
-- ============================================================
CREATE TABLE IF NOT EXISTS ud_address (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id         BIGINT          NOT NULL               COMMENT '用户ID',
    receiver_name   VARCHAR(32)     NOT NULL               COMMENT '收件人姓名',
    phone           VARCHAR(20)     NOT NULL               COMMENT '收件人电话',
    province        VARCHAR(50)                             COMMENT '省份',
    city            VARCHAR(50)                             COMMENT '城市',
    district        VARCHAR(50)                             COMMENT '区/县',
    detail_address  VARCHAR(200)    NOT NULL               COMMENT '详细地址',
    is_default      TINYINT         DEFAULT 0               COMMENT '是否默认地址：1=是 0=否',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_ud_address_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户地址表';

-- ============================================================
-- 初始化角色数据
-- ============================================================
INSERT IGNORE INTO sys_role (name, code, description) VALUES
    ('普通用户', 'ROLE_USER', '平台注册用户，拥有基础权限'),
    ('商家',     'ROLE_MERCHANT', '入驻商家，拥有商品和订单管理权限'),
    ('管理员',   'ROLE_ADMIN', '系统管理员，拥有全部权限');

-- ============================================================
-- 商家表
-- ============================================================
CREATE TABLE IF NOT EXISTS md_merchant (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id         BIGINT          NOT NULL               COMMENT '用户ID（一个用户只能开一家店）',
    shop_name       VARCHAR(100)    NOT NULL               COMMENT '店铺名称',
    description     VARCHAR(500)                            COMMENT '店铺简介',
    status          TINYINT         DEFAULT 0               COMMENT '状态：0=待审核 1=已开通 2=已关闭',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_md_merchant_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家表';

-- ============================================================
-- 商品分类表
-- ============================================================
CREATE TABLE IF NOT EXISTS pd_category (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name            VARCHAR(50)     NOT NULL               COMMENT '分类名称',
    parent_id       BIGINT                                  COMMENT '父分类ID（支持多级分类）',
    sort_order      INT             DEFAULT 0               COMMENT '排序号',
    merchant_id     BIGINT                                  COMMENT '商家ID（空=平台公共分类）',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_pd_category_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ============================================================
-- 商品表
-- ============================================================
CREATE TABLE IF NOT EXISTS pd_product (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    merchant_id     BIGINT          NOT NULL               COMMENT '商家ID',
    category_id     BIGINT                                  COMMENT '分类ID',
    name            VARCHAR(200)    NOT NULL               COMMENT '商品名称',
    description     VARCHAR(2000)                           COMMENT '商品描述',
    price           DECIMAL(10,2)   NOT NULL               COMMENT '价格',
    stock           INT             DEFAULT 0               COMMENT '库存',
    images          VARCHAR(2000)                           COMMENT '图片URL（JSON数组或逗号分隔）',
    status          TINYINT         DEFAULT 0               COMMENT '状态：0=下架 1=上架',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_pd_product_merchant_id (merchant_id),
    INDEX idx_pd_product_category_id (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ============================================================
-- 商品 SKU 表
-- ============================================================
CREATE TABLE IF NOT EXISTS pd_product_sku (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    product_id  BIGINT          NOT NULL               COMMENT '商品ID',
    sku_name    VARCHAR(200)    NOT NULL               COMMENT '规格名称，如 黑色/M',
    spec_json   VARCHAR(500)                           COMMENT '结构化规格JSON',
    price       DECIMAL(10,2)   NOT NULL               COMMENT 'SKU价格',
    stock       INT             NOT NULL DEFAULT 0     COMMENT 'SKU库存',
    image       VARCHAR(500)                           COMMENT 'SKU图片',
    is_default  TINYINT         NOT NULL DEFAULT 0     COMMENT '1=默认规格',
    status      TINYINT         NOT NULL DEFAULT 1     COMMENT '1=启用 0=停用',
    created_at  DATETIME        DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_sku_name (product_id, sku_name),
    INDEX idx_product_sku_product (product_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品SKU表';

-- ============================================================
-- 购物车表
-- ============================================================
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀商品活动';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀订单';

CREATE TABLE IF NOT EXISTS cart (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id         BIGINT          NOT NULL               COMMENT '用户ID',
    product_id      BIGINT          NOT NULL               COMMENT '商品ID',
    sku_id          BIGINT          NOT NULL               COMMENT 'SKU ID',
    quantity        INT             DEFAULT 1               COMMENT '数量',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_cart_user_sku (user_id, sku_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- ============================================================
-- 订单表
-- ============================================================
CREATE TABLE IF NOT EXISTS od_order (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    parent_order_id BIGINT                                  COMMENT '父订单ID：子订单字段，旧订单为空',
    user_id         BIGINT          NOT NULL               COMMENT '用户ID',
    merchant_id     BIGINT                                  COMMENT '商家ID：商家子订单字段',
    order_no        VARCHAR(32)     NOT NULL               COMMENT '订单号',
    total_amount    DECIMAL(10,2)   NOT NULL               COMMENT '订单总额',
    status          TINYINT         DEFAULT 0               COMMENT '状态：0=待付款 1=待发货 2=待收货 3=已完成 4=已取消',
    order_type      TINYINT         NOT NULL DEFAULT 1      COMMENT '1=旧独立订单 2=父订单 3=商家子订单',
    receiver_name   VARCHAR(50)     NOT NULL               COMMENT '收货人姓名',
    receiver_phone  VARCHAR(20)     NOT NULL               COMMENT '收货人电话',
    receiver_address VARCHAR(255)   NOT NULL               COMMENT '收货地址',
    remark          VARCHAR(500)                            COMMENT '订单备注',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_od_order_order_no (order_no),
    INDEX idx_od_order_user_id (user_id),
    INDEX idx_od_order_parent (parent_order_id),
    INDEX idx_od_order_merchant_status (merchant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================================================
-- 订单明细表
-- ============================================================
CREATE TABLE IF NOT EXISTS od_order_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    order_id        BIGINT          NOT NULL               COMMENT '订单ID',
    product_id      BIGINT          NOT NULL               COMMENT '商品ID',
    merchant_id     BIGINT                                  COMMENT '商家ID（下单时快照）',
    sku_id          BIGINT                                  COMMENT 'SKU ID（下单时快照）',
    sku_name        VARCHAR(200)                            COMMENT 'SKU名称（下单时快照）',
    product_name    VARCHAR(200)    NOT NULL               COMMENT '商品名称（下单时快照）',
    product_price   DECIMAL(10,2)   NOT NULL               COMMENT '商品价格（下单时快照）',
    product_image   VARCHAR(500)                            COMMENT '商品图片（下单时快照）',
    quantity        INT             NOT NULL               COMMENT '购买数量',
    subtotal        DECIMAL(10,2)   NOT NULL               COMMENT '小计金额',
    INDEX idx_od_order_item_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ============================================================
-- 秒杀活动 / 聊天等表
-- ============================================================

-- ============================================================
-- 智能体聊天消息表
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id      VARCHAR(64)     NOT NULL               COMMENT '会话ID（user:{userId}）',
    user_id         BIGINT                                  COMMENT '用户ID',
    role            VARCHAR(32)     NOT NULL               COMMENT '角色：user / assistant / 智能体名称',
    content         TEXT            NOT NULL               COMMENT '消息内容',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_chat_message_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体聊天消息表';

-- ============================================================
-- 广场动态表
-- ============================================================
CREATE TABLE IF NOT EXISTS feed_post (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id         BIGINT          NOT NULL               COMMENT '发布用户ID',
    username        VARCHAR(32)     NOT NULL               COMMENT '发布用户名（冗余）',
    content         VARCHAR(500)    NOT NULL               COMMENT '动态内容',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_feed_post_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='广场动态表';

-- ============================================================
-- 会话摘要表（长对话滚动压缩）
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_summary (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id      VARCHAR(64)     NOT NULL               COMMENT '会话ID（user:{userId}）',
    summary         TEXT            NOT NULL               COMMENT '摘要内容',
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_chat_summary_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话摘要表';

-- ============================================================
-- 用户画像表（长期记忆）
-- ============================================================
CREATE TABLE IF NOT EXISTS user_profile (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id         BIGINT          NOT NULL               COMMENT '用户ID',
    profile_key     VARCHAR(50)     NOT NULL               COMMENT '画像项（如 name/preference/hobby）',
    profile_value   VARCHAR(500)    NOT NULL               COMMENT '画像值',
    updated_at      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE INDEX uk_user_profile_key (user_id, profile_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像表';

-- ============================================================
-- 智能体思考轨迹表（决策过程可视化）
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_trace (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    session_id      VARCHAR(64)     NOT NULL               COMMENT '会话ID（user:{userId}）',
    user_id         BIGINT                                  COMMENT '用户ID',
    message         VARCHAR(1000)                           COMMENT '用户消息',
    intent          VARCHAR(32)                             COMMENT '意图分类结果',
    route_agent     VARCHAR(64)                             COMMENT '路由到的智能体',
    steps           TEXT                                    COMMENT '工具调用步骤 JSON',
    reply           TEXT                                    COMMENT '智能体回复',
    duration_ms     BIGINT                                  COMMENT '耗时(毫秒)',
    created_at      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_chat_trace_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体思考轨迹表';

-- ============================================================
-- 用户关注关系（商家 / 商品）
-- 用一张多态表存两类关注：target_type 1=商家 2=商品。
-- 以后要加「关注品类」，只扩 target_type 枚举即可，不用再加表。
-- ============================================================
CREATE TABLE IF NOT EXISTS ud_follow (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id     BIGINT      NOT NULL               COMMENT '关注者用户ID',
    target_type TINYINT     NOT NULL               COMMENT '关注对象类型：1=商家 2=商品',
    target_id   BIGINT      NOT NULL               COMMENT '关注对象ID',
    created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_follow (user_id, target_type, target_id),
    INDEX idx_follow_target (target_type, target_id),
    INDEX idx_follow_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注关系（商家/商品）';

-- ============================================================
-- 支付单
-- 订单管「交易」，支付单管「资金」，两者分开：
-- 一个订单可能有多笔支付尝试（超时关闭后重新发起），
-- 独立成表才能完整记录每笔尝试，也才谈得上对账。
-- ============================================================
CREATE TABLE IF NOT EXISTS od_payment (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    payment_no  VARCHAR(64)   NOT NULL               COMMENT '支付单号',
    order_id    BIGINT        NOT NULL               COMMENT '关联订单ID',
    user_id     BIGINT        NOT NULL               COMMENT '付款用户ID',
    amount      DECIMAL(10,2) NOT NULL               COMMENT '支付金额（下单时快照）',
    channel     TINYINT       NOT NULL               COMMENT '支付渠道：1=模拟支付',
    status      TINYINT       NOT NULL DEFAULT 0     COMMENT '0=待支付 1=支付成功 2=已关闭',
    paid_at     DATETIME      NULL                   COMMENT '支付完成时间',
    created_at  DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_payment_no (payment_no),
    INDEX idx_payment_order (order_id),
    INDEX idx_payment_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单';

-- ============================================================
-- 售后单
-- 独立于订单状态机：售后要能被拒绝、被撤销、被平台介入，
-- 还要支持「一个订单里只退其中一件」。
-- ============================================================
CREATE TABLE IF NOT EXISTS od_after_sale (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    after_sale_no  VARCHAR(64)   NOT NULL               COMMENT '售后单号',
    order_id       BIGINT        NOT NULL               COMMENT '订单ID',
    order_item_id  BIGINT        NOT NULL               COMMENT '订单明细ID（支持按件售后）',
    user_id        BIGINT        NOT NULL               COMMENT '买家ID',
    merchant_id    BIGINT        NOT NULL               COMMENT '商家ID（下单时快照）',
    type           TINYINT       NOT NULL DEFAULT 1     COMMENT '1=仅退款 2=退货退款',
    reason         VARCHAR(200)  NOT NULL               COMMENT '申请原因',
    description    VARCHAR(500)  NULL                   COMMENT '补充说明',
    amount         DECIMAL(10,2) NOT NULL               COMMENT '申请退款金额',
    status         TINYINT       NOT NULL DEFAULT 0     COMMENT '0待审核 1已同意 2已拒绝 3已完成 4已撤销 5已寄回待收货',
    reject_reason  VARCHAR(200)  NULL                   COMMENT '商家拒绝理由',
    return_company VARCHAR(50)   NULL                   COMMENT '退货物流公司',
    return_tracking_no VARCHAR(100) NULL                COMMENT '退货物流单号',
    returned_at    DATETIME      NULL                   COMMENT '买家寄回时间',
    received_at    DATETIME      NULL                   COMMENT '商家确认收货时间',
    applied_at     DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    handled_at     DATETIME      NULL                   COMMENT '处理时间',
    created_at     DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_after_sale_no (after_sale_no),
    INDEX idx_as_order_item (order_item_id),
    INDEX idx_as_user (user_id),
    INDEX idx_as_merchant (merchant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后单';

-- ============================================================
-- 退款流水
-- 和支付单同理：资金动作独立记录，才能对账
-- ============================================================
CREATE TABLE IF NOT EXISTS od_refund (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    refund_no     VARCHAR(64)   NOT NULL               COMMENT '退款流水号',
    after_sale_id BIGINT        NOT NULL               COMMENT '关联售后单',
    order_id      BIGINT        NOT NULL               COMMENT '订单ID',
    payment_no    VARCHAR(64)   NULL                   COMMENT '原支付单号',
    amount        DECIMAL(10,2) NOT NULL               COMMENT '退款金额',
    status        TINYINT       NOT NULL DEFAULT 0     COMMENT '0退款中 1退款成功 2退款失败',
    refunded_at   DATETIME      NULL                   COMMENT '退款完成时间',
    created_at    DATETIME      DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_refund_no (refund_no),
    INDEX idx_refund_after_sale (after_sale_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款流水';

-- ============================================================
-- 站内通知
-- dedup_key 决定去重粒度：
--   一次性事件（发货、退款）留空 -> 永远只发一条
--   可重复事件（降价）填日期     -> 同一对象同一天只发一条
-- 用唯一索引兜底，而不是只靠应用层查一遍再插（并发下会漏）。
-- ============================================================
CREATE TABLE IF NOT EXISTS ud_notification (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id    BIGINT       NOT NULL               COMMENT '接收人',
    type       TINYINT      NOT NULL               COMMENT '1订单 2售后 3关注商品降价 4关注店铺上新',
    title      VARCHAR(100) NOT NULL               COMMENT '标题',
    content    VARCHAR(500) NULL                   COMMENT '正文',
    ref_type   TINYINT      NULL                   COMMENT '关联对象：1订单 2商品 3店铺',
    ref_id     BIGINT       NULL                   COMMENT '关联对象ID，前端据此跳转',
    is_read    TINYINT      NOT NULL DEFAULT 0     COMMENT '0未读 1已读',
    dedup_key  VARCHAR(32)  NOT NULL DEFAULT ''    COMMENT '去重键：一次性事件留空，按天去重填日期',
    created_at DATETIME     DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_notify_dedup (user_id, type, ref_type, ref_id, dedup_key),
    INDEX idx_notify_user (user_id, is_read, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知';

-- ============================================================
-- 智能工单客服基础表
-- 具体字段与 deploy/sql/support_ticket.sql 保持一致。
-- ============================================================
CREATE TABLE IF NOT EXISTS sp_support_ticket (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    ticket_no         VARCHAR(64)   NOT NULL               COMMENT '工单号',
    user_id           BIGINT        NOT NULL               COMMENT '用户ID',
    merchant_id       BIGINT        NULL                   COMMENT '关联商家ID',
    order_id          BIGINT        NULL                   COMMENT '关联订单ID',
    after_sale_id     BIGINT        NULL                   COMMENT '关联售后单ID',
    channel           VARCHAR(32)   NOT NULL DEFAULT 'web' COMMENT '来源渠道',
    client_request_id VARCHAR(64)   NULL                   COMMENT '客户端幂等键',
    category          VARCHAR(80)   NOT NULL DEFAULT 'general' COMMENT '工单分类',
    priority          VARCHAR(16)   NOT NULL DEFAULT 'NORMAL' COMMENT 'LOW/NORMAL/HIGH/URGENT',
    status            INT           NOT NULL DEFAULT 10    COMMENT '状态码',
    subject           VARCHAR(200)  NOT NULL               COMMENT '工单主题',
    summary           TEXT          NULL                   COMMENT '智能体摘要',
    assignee_type     VARCHAR(32)   NULL                   COMMENT '处理方类型',
    assignee_id       BIGINT        NULL                   COMMENT '处理方ID',
    sla_deadline      DATETIME      NULL                   COMMENT 'SLA截止时间',
    last_message_at   DATETIME      NULL                   COMMENT '最后消息时间',
    resolved_at       DATETIME      NULL                   COMMENT '解决时间',
    closed_at         DATETIME      NULL                   COMMENT '关闭时间',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_support_ticket_no (ticket_no),
    UNIQUE KEY uk_support_ticket_client_request (user_id, client_request_id),
    INDEX idx_support_ticket_user (user_id, status, last_message_at),
    INDEX idx_support_ticket_assignee (assignee_type, assignee_id, status),
    INDEX idx_support_ticket_order (order_id),
    INDEX idx_support_ticket_after_sale (after_sale_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能工单';

CREATE TABLE IF NOT EXISTS sp_support_message (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    ticket_id          BIGINT       NOT NULL               COMMENT '工单ID',
    sender_type        VARCHAR(32)  NOT NULL               COMMENT 'USER/AGENT/MERCHANT/HUMAN/SYSTEM',
    sender_id          BIGINT       NULL                   COMMENT '发送者ID',
    message_type       VARCHAR(32)  NOT NULL DEFAULT 'TEXT' COMMENT '消息类型',
    content            TEXT         NOT NULL               COMMENT '消息内容',
    structured_payload TEXT         NULL                   COMMENT '结构化JSON',
    client_message_id  VARCHAR(64)  NULL                   COMMENT '客户端消息幂等键',
    visibility         VARCHAR(16)  NOT NULL DEFAULT 'EXTERNAL' COMMENT 'EXTERNAL/INTERNAL',
    trace_id           VARCHAR(64)  NULL                   COMMENT '智能体轨迹ID',
    created_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_support_message_ticket (ticket_id, id),
    INDEX idx_support_message_trace (trace_id),
    UNIQUE KEY uk_support_message_client (ticket_id, client_message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单消息';

CREATE TABLE IF NOT EXISTS sp_support_event (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    ticket_id   BIGINT       NOT NULL               COMMENT '工单ID',
    event_type  VARCHAR(64)  NOT NULL               COMMENT '事件类型',
    from_status INT          NULL                   COMMENT '原状态',
    to_status   INT          NULL                   COMMENT '新状态',
    actor_type  VARCHAR(32)  NOT NULL               COMMENT '操作方类型',
    actor_id    BIGINT       NULL                   COMMENT '操作方ID',
    payload     TEXT         NULL                   COMMENT '事件JSON',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_support_event_ticket (ticket_id, id),
    INDEX idx_support_event_type (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单事件时间线';

CREATE TABLE IF NOT EXISTS sp_support_action (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    action_no             VARCHAR(64)   NOT NULL               COMMENT '动作编号',
    ticket_id             BIGINT        NOT NULL               COMMENT '工单ID',
    action_type           VARCHAR(64)   NOT NULL               COMMENT '动作类型',
    status                VARCHAR(32)   NOT NULL               COMMENT '动作状态',
    payload               TEXT          NULL                   COMMENT '动作提案JSON',
    requires_confirmation TINYINT(1)    NOT NULL DEFAULT 1     COMMENT '是否需要确认',
    idempotency_key       VARCHAR(128)  NOT NULL               COMMENT '幂等键',
    executed_at           DATETIME      NULL                   COMMENT '执行时间',
    result_payload        TEXT          NULL                   COMMENT '执行结果JSON',
    created_by            BIGINT        NULL                   COMMENT '创建人ID',
    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_support_action_no (action_no),
    UNIQUE KEY uk_support_action_idempotency (idempotency_key),
    INDEX idx_support_action_ticket (ticket_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单动作';

CREATE TABLE IF NOT EXISTS sp_support_agent_run (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    run_no           VARCHAR(64)  NOT NULL               COMMENT '运行编号',
    ticket_id        BIGINT       NOT NULL               COMMENT '工单ID',
    status           VARCHAR(32)  NOT NULL               COMMENT '运行状态',
    provider         VARCHAR(64)  NULL                   COMMENT '模型供应商',
    model            VARCHAR(128) NULL                   COMMENT '模型名称',
    prompt_version   VARCHAR(64)  NULL                   COMMENT '提示词版本',
    request_payload  LONGTEXT     NULL                   COMMENT '请求JSON',
    response_payload LONGTEXT     NULL                   COMMENT '响应JSON',
    error_message    TEXT         NULL                   COMMENT '错误信息',
    client_request_id VARCHAR(64) NULL                   COMMENT '客户端请求幂等键',
    cancel_requested TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '是否请求取消',
    version          INT          NOT NULL DEFAULT 0     COMMENT '乐观锁版本',
    last_completed_step VARCHAR(64) NULL                 COMMENT '最后完成步骤',
    worker_id        VARCHAR(128) NULL                   COMMENT 'Worker所有者',
    lease_expires_at DATETIME     NULL                   COMMENT '租约到期时间',
    last_heartbeat_at DATETIME    NULL                   COMMENT '最后心跳时间',
    started_at       DATETIME     NULL                   COMMENT '开始时间',
    finished_at      DATETIME     NULL                   COMMENT '结束时间',
    duration_ms      BIGINT       NULL                   COMMENT '耗时毫秒',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_support_agent_run_no (run_no),
    UNIQUE KEY uk_support_agent_run_client (ticket_id, client_request_id),
    INDEX idx_support_agent_run_ticket (ticket_id, id),
    INDEX idx_support_agent_run_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体运行轨迹';

CREATE TABLE IF NOT EXISTS sp_support_agent_run_step (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    run_id         BIGINT       NOT NULL               COMMENT '运行ID',
    step_name      VARCHAR(64)  NOT NULL               COMMENT '断点步骤',
    status         VARCHAR(32)  NOT NULL               COMMENT 'RUNNING/SUCCEEDED/FAILED/SKIPPED',
    input_hash     VARCHAR(128) NULL                   COMMENT '输入哈希',
    output_payload LONGTEXT     NULL                   COMMENT '结构化输出JSON',
    error_message  TEXT         NULL                   COMMENT '错误信息',
    attempt        INT          NOT NULL DEFAULT 1     COMMENT '尝试次数',
    started_at     DATETIME     NULL                   COMMENT '开始时间',
    finished_at    DATETIME     NULL                   COMMENT '结束时间',
    duration_ms    BIGINT       NULL                   COMMENT '耗时毫秒',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_support_agent_step_run (run_id, id),
    INDEX idx_support_agent_step_name (run_id, step_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体步骤断点';

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
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    aggregate_type VARCHAR(64)  NOT NULL               COMMENT '聚合类型',
    aggregate_id   BIGINT       NOT NULL               COMMENT '聚合ID',
    event_type     VARCHAR(64)  NOT NULL               COMMENT '事件类型',
    transport      VARCHAR(16)  NOT NULL DEFAULT 'RABBIT' COMMENT 'RABBIT/KAFKA',
    destination    VARCHAR(128) NULL                   COMMENT 'Exchange或Kafka主题',
    payload        LONGTEXT     NOT NULL               COMMENT '事件JSON',
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/SENDING/SENT/FAILED',
    retry_count    INT          NOT NULL DEFAULT 0     COMMENT '重试次数',
    next_retry_at  DATETIME     NOT NULL               COMMENT '下次重试时间',
    claim_token    VARCHAR(64)  NULL                   COMMENT '发布者认领令牌',
    lease_expires_at DATETIME   NULL                   COMMENT '发布者租约到期时间',
    last_error     TEXT         NULL                   COMMENT '最后错误',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_support_outbox_pending (status, next_retry_at, id),
    INDEX idx_support_outbox_claim (status, lease_expires_at, next_retry_at, id),
    INDEX idx_support_outbox_aggregate (aggregate_type, aggregate_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体 outbox';

CREATE TABLE IF NOT EXISTS sp_support_agent_evaluation (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    run_id       BIGINT       NOT NULL               COMMENT 'Agent Run ID',
    score        TINYINT      NOT NULL               COMMENT '质量评分 1-5',
    tags         VARCHAR(255) NULL                   COMMENT '评价标签',
    notes        VARCHAR(2000) NULL                  COMMENT '人工复核说明',
    evaluator_id BIGINT       NOT NULL               COMMENT '审核管理员ID',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_support_agent_evaluation_run (run_id),
    INDEX idx_support_agent_evaluation_score (score, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体质量评测';
