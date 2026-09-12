-- =====================================================================
-- MLS V1 core schema (M4 deliverable: DB model frozen, GOV-0006 §6.5)
-- 状态枚举与 OD-0002 §17 契约命名一一对应；version 列承载 CAS 条件更新
-- (OD-0002 §19)；pay_order 部分唯一索引实现同罚款建单独占 (BR-0047/SC-Q14)。
-- =====================================================================

-- pg_trgm 扩展（T4 检索；由迁移统一建立，幂等；先于使用它的索引）
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 账户与读者 ------------------------------------------------------------
CREATE TABLE account (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(256) NOT NULL,
    display_name  VARCHAR(128) NOT NULL,
    role          VARCHAR(32)  NOT NULL CHECK (role IN ('READER', 'LIBRARIAN', 'ADMIN')),  -- 一账户一角色 BR-0031
    status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED')),
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 书目 ------------------------------------------------------------------
CREATE TABLE book_title (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    isbn       VARCHAR(20),
    title      VARCHAR(256) NOT NULL,
    author     VARCHAR(256),
    publisher  VARCHAR(128),
    category   VARCHAR(64),
    summary    TEXT,
    price_cents BIGINT      NOT NULL CHECK (price_cents >= 0),  -- 罚款封顶基数 BR-0017
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_book_title_trgm ON book_title USING gin (title gin_trgm_ops);  -- T4 检索
CREATE INDEX ix_book_title_category ON book_title (category);

-- 实体副本（六态，RA-0008 §14）-------------------------------------------
CREATE TABLE book_copy (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title_id   BIGINT       NOT NULL REFERENCES book_title (id),
    copy_no    VARCHAR(32)  NOT NULL,
    status     VARCHAR(32)  NOT NULL DEFAULT 'PENDING_SHELVING'
               CHECK (status IN ('PENDING_SHELVING', 'SHELF_READY', 'RESERVED',
                                 'LENT', 'MAINTENANCE', 'REMOVED')),  -- 已下架不可逆 BR-0044
    version    BIGINT       NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (title_id, copy_no)
);
CREATE INDEX ix_book_copy_title_status ON book_copy (title_id, status);

-- 借阅事务（RA-0008 §13；终态封闭 BR-0032/0044）---------------------------
CREATE TABLE borrow_transaction (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reader_id        BIGINT      NOT NULL REFERENCES account (id),
    title_id         BIGINT      NOT NULL REFERENCES book_title (id),
    copy_id          BIGINT      REFERENCES book_copy (id),        -- 人工处理中(入口)无绑定
    status           VARCHAR(32) NOT NULL
                     CHECK (status IN ('AWAIT', 'CONFIRMED', 'MANUAL_HANDLING', 'WAITLIST',
                                       'LENT', 'NOT_BORROWED', 'RETURNED', 'RETURN_ABNORMAL')),
    reservation_date DATE,            -- 预约/候补业务日
    pickup_slot      VARCHAR(32),     -- 备书与统计，不作状态守卫 BR-0020
    due_date         DATE,            -- LENT 起按批准借期
    queued_at        TIMESTAMPTZ,     -- WAITLIST 队列 FIFO 锚点（队首先到先得 BR-0041）
    return_condition VARCHAR(32),     -- 归还物理判定：INTACT / DAMAGED / LOST
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_borrow_tx_reader ON borrow_transaction (reader_id, status);
CREATE INDEX ix_borrow_tx_title_waitlist ON borrow_transaction (title_id, queued_at)
    WHERE status = 'WAITLIST';
-- 重复预约守卫（同读者同书目存在未终结事务时禁止重复，BR-0020 D 条件）
CREATE UNIQUE INDEX uq_borrow_tx_open_per_reader_title ON borrow_transaction (reader_id, title_id)
    WHERE status IN ('AWAIT', 'CONFIRMED', 'MANUAL_HANDLING', 'WAITLIST', 'LENT');

-- 罚款（BR-0017；已结清后不得再建单 BR-0047/D14）---------------------------
CREATE TABLE fine (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reader_id   BIGINT      NOT NULL REFERENCES account (id),
    source_tx_id BIGINT     NOT NULL REFERENCES borrow_transaction (id),
    amount_cents BIGINT     NOT NULL CHECK (amount_cents >= 0),
    status      VARCHAR(32) NOT NULL DEFAULT 'DUE'
                CHECK (status IN ('DUE', 'SETTLED', 'WAIVED')),
    settled_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_tx_id)             -- 一次归还至多一条罚款（不重复计罚 BR-0019）
);
CREATE UNIQUE INDEX uq_fine_open_per_reader ON fine (reader_id)
    WHERE status = 'DUE';             -- 同读者未缴罚款唯一存在（F 条件）

-- 罚款订单（四态：未建单=无行；占用中/已关闭/已支付）------------------------
CREATE TABLE payment_order (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fine_id    BIGINT      NOT NULL REFERENCES fine (id),
    status     VARCHAR(32) NOT NULL DEFAULT 'OCCUPIED'
               CHECK (status IN ('OCCUPIED', 'CLOSED', 'PAID')),   -- §16；终态吸收 PAY-D15
    expires_at TIMESTAMPTZ NOT NULL,   -- 订单自身到期（触发关闭事务 SC-Q13）
    closed_at  TIMESTAMPTZ,
    paid_at    TIMESTAMPTZ,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- 建单独占（SC-Q14/D7~D9）：同一罚款至多一个未释放订单，数据库层强制
CREATE UNIQUE INDEX uq_payment_order_unreleased ON payment_order (fine_id)
    WHERE status = 'OCCUPIED';

-- 闭馆控制与批次（OD-0002 §9；严格 T 截止经控制状态门控写入）----------------
CREATE TABLE closing_control (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope           VARCHAR(32) NOT NULL DEFAULT 'GLOBAL' UNIQUE,  -- 全馆隔离 SC-Q1
    status          VARCHAR(32) NOT NULL DEFAULT 'OPEN'
                    CHECK (status IN ('OPEN', 'ISOLATED', 'REOPENED')),
    current_date    DATE,
    retry_budget    INT      NOT NULL DEFAULT 1,   -- SC-Q8 有限自动重试（配置化）
    version         BIGINT   NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE closing_batch (
    business_date DATE      PRIMARY KEY,           -- 原闭馆业务日期固定 SC-Q7
    status        VARCHAR(32) NOT NULL
                  CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'RETRYING', 'MANUAL')),
    attempts      INT       NOT NULL DEFAULT 0,
    version       BIGINT    NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 高风险复核（BR-0021/0048；批准/变更/事件同一事务承载）---------------------
CREATE TABLE review_request (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    applicant_id BIGINT      NOT NULL REFERENCES account (id),
    target_type  VARCHAR(64) NOT NULL,   -- FINE_WAIVER / FORCE_CIRCULATION / ACCOUNT_*, ...
    target_id    BIGINT      NOT NULL,
    reason       TEXT        NOT NULL,
    decision     VARCHAR(32) NOT NULL DEFAULT 'PENDING'
                 CHECK (decision IN ('PENDING', 'APPROVED', 'REJECTED')),
    decided_by   BIGINT      REFERENCES account (id),
    decided_at   TIMESTAMPTZ,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 事务性 Outbox（业务写入同事务登记；至少一次投递＋消费幂等）----------------
CREATE TABLE outbox_event (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agg_type        VARCHAR(64) NOT NULL,
    agg_id          BIGINT      NOT NULL,
    event_type      VARCHAR(64) NOT NULL,
    payload         JSONB       NOT NULL,
    published_at    TIMESTAMPTZ,           -- NULL = 待投递
    publish_attempts INT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_outbox_unpublished ON outbox_event (id) WHERE published_at IS NULL;

-- 幂等键（BR-0042 效果唯一；重复提交返回首次结果）---------------------------
CREATE TABLE idempotency_key (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_key  VARCHAR(128) NOT NULL UNIQUE,
    request_hash VARCHAR(64)  NOT NULL,
    response_snapshot JSONB,
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 站内通知（到馆/无需到馆各一次；临近优先排序 DEC-0020）---------------------
CREATE TABLE notification (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reader_id        BIGINT      NOT NULL REFERENCES account (id),
    kind             VARCHAR(32) NOT NULL CHECK (kind IN ('VISIT', 'NO_VISIT')),
    biz_ref          BIGINT      NOT NULL,
    reservation_date DATE        NOT NULL,   -- 同日互斥、跨日临近排序锚点
    read_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_notification_reader_unread ON notification (reader_id, reservation_date)
    WHERE read_at IS NULL;

-- 审计事件（失败审计独立提交，不随业务回滚丢弃 SC-Q5/Q9）--------------------
CREATE TABLE audit_event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_id    BIGINT,
    event_type  VARCHAR(64) NOT NULL,
    detail      JSONB
);
CREATE INDEX ix_audit_event_type_time ON audit_event (event_type, occurred_at);

-- AI 助手（会话/消息/额度；DEC-0021/0022）-----------------------------------
CREATE TABLE ai_session (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id BIGINT      NOT NULL REFERENCES account (id),
    status     VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
               CHECK (status IN ('ACTIVE', 'MAX_TURNS', 'QUOTA_BLOCKED', 'ERROR', 'CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_message (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id BIGINT      NOT NULL REFERENCES ai_session (id),
    role       VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'TOOL', 'SYSTEM')),
    content    TEXT,
    tool_payload JSONB,               -- tool_calls 线格式（结构化留痕）
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_quota_usage (
    account_id BIGINT      NOT NULL REFERENCES account (id),
    usage_date DATE        NOT NULL,
    model_calls INT        NOT NULL DEFAULT 0,   -- 按模型调用计数 DEC-0021
    version    BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, usage_date)
);
