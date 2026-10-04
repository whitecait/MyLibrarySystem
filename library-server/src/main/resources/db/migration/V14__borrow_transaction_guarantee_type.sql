ALTER TABLE borrow_transaction
    ADD COLUMN guarantee_type VARCHAR(32)
        CHECK (guarantee_type IN ('DIRECT_CONFIRMATION', 'WAITLIST'));

-- Only the positive reservation-creation audit fact can identify the original guarantee.
-- Invalid ids are excluded before the guarded bigint cast. Other event types never enter
-- this set, even when they carry the same relatedId.
WITH reservation_facts AS (
    SELECT CASE
               WHEN jsonb_typeof(detail -> 'relatedId') = 'number'
                   AND detail ->> 'relatedId' ~ '^[1-9][0-9]*$'
                   AND pg_input_is_valid(detail ->> 'relatedId', 'bigint')
               THEN (detail ->> 'relatedId')::bigint
           END AS tx_id,
           CASE WHEN jsonb_typeof(detail -> 'outcome') = 'string'
                THEN detail ->> 'outcome' END AS outcome,
           jsonb_typeof(detail -> 'outcome') = 'string'
               AND detail ->> 'outcome' IN ('CONFIRMED', 'WAITLIST_ENQUEUED') AS recognized
    FROM audit_event
    WHERE event_type = 'RESERVATION_DECIDED'
      AND jsonb_typeof(detail) = 'object'
),
fact_summary AS (
    SELECT tx_id,
           count(*) AS fact_count,
           count(*) FILTER (WHERE recognized) AS recognized_count,
           count(DISTINCT outcome) FILTER (WHERE recognized) AS outcome_count,
           min(outcome) FILTER (WHERE recognized) AS outcome
    FROM reservation_facts
    WHERE tx_id > 0
    GROUP BY tx_id
)
UPDATE borrow_transaction AS tx
SET guarantee_type = CASE facts.outcome
                         WHEN 'CONFIRMED' THEN 'DIRECT_CONFIRMATION'
                         WHEN 'WAITLIST_ENQUEUED' THEN 'WAITLIST'
                     END
FROM fact_summary AS facts
WHERE tx.id = facts.tx_id
  AND facts.fact_count = facts.recognized_count
  AND facts.outcome_count = 1;
