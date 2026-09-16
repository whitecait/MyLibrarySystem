-- V5: B1 身份会话与书目阅读内容（实现型详细设计修订，不改写 V1~V4）
-- 差额处置（M5 计划 §3.1，已批准）：
--   1) 登录会话存储：服务端可吊销 opaque 会话（endCurrentSession 需吊销语义）
--   2) 阅读内容持久化：CATALOG 拥有的分页内容结构（FR-0045）
--   3) 复核动作快照：B1 账户变更申请即写入动作与负载，B3 批准时重验目标版本
--   4) book_title.publish_year：OpenAPI CreateTitleRequest/TitleData 往返字段
--      （V1 无对应列；可空，不改历史行）

-- 1) 登录会话 ---------------------------------------------------------------
CREATE TABLE auth_session (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id  BIGINT       NOT NULL REFERENCES account (id),
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,   -- SHA-256(opaque token) hex
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    ended_at    TIMESTAMPTZ                       -- 非空 = 已吊销
);
CREATE INDEX ix_auth_session_account ON auth_session (account_id);

-- 2) 书目阅读内容（CATALOG 拥有；按页持久化）---------------------------------
CREATE TABLE book_title_content_page (
    title_id      BIGINT      NOT NULL REFERENCES book_title (id),
    page_no       INT         NOT NULL CHECK (page_no >= 1),
    content_type  VARCHAR(32) NOT NULL,
    text          TEXT        NOT NULL,
    PRIMARY KEY (title_id, page_no)
);

-- 3) 复核动作快照（封闭动作 + 类型化负载 + 目标版本快照）----------------------
ALTER TABLE review_request
    ADD COLUMN action_type    VARCHAR(64),   -- ACCOUNT_CHANGE / CIRCULATION_EXCEPTION / REFUND
    ADD COLUMN action_payload JSONB,          -- 类型化负载（封闭动作参数）
    ADD COLUMN target_version BIGINT,         -- 发起时目标聚合版本快照
    ADD COLUMN target_outcome VARCHAR(64);    -- 批准执行后的目标结果摘要（B3 写入）

-- 4) 书目出版年份（OpenAPI 可选字段往返）--------------------------------------
ALTER TABLE book_title
    ADD COLUMN publish_year SMALLINT;
