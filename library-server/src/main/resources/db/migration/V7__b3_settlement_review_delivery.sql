-- B3 settlement/review/delivery persistence. V6 is already occupied by B2.
CREATE TABLE payment_callback_event (
    event_id     VARCHAR(128) PRIMARY KEY,
    order_id     BIGINT NOT NULL REFERENCES payment_order(id),
    result       VARCHAR(16) NOT NULL CHECK (result IN ('SUCCESS','FAILURE')),
    occurred_at  TIMESTAMPTZ NOT NULL,
    received_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    outcome      VARCHAR(64) NOT NULL,
    first_response JSONB
);

CREATE TABLE refund_record (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id          BIGINT NOT NULL REFERENCES payment_order(id),
    amount_cents      BIGINT NOT NULL CHECK (amount_cents > 0),
    status            VARCHAR(32) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    review_request_id BIGINT NOT NULL REFERENCES review_request(id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE reconciliation_record (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    business_date DATE NOT NULL UNIQUE,
    counts        JSONB NOT NULL,
    exceptions    JSONB NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_notification_business_effect
    ON notification(reader_id, biz_ref, kind);
