-- One receipt per consumer and immutable event, not per aggregate/event type.
-- The consumer inserts this receipt in the same transaction as its effect.
CREATE TABLE event_consumer_receipt (
    consumer_id VARCHAR(64) NOT NULL,
    event_id BIGINT NOT NULL REFERENCES outbox_event (id),
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_id, event_id)
);
