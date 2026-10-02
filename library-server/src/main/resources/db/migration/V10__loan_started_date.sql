ALTER TABLE borrow_transaction
    ADD COLUMN loan_started_date DATE;

-- Existing live loans used a 30-day initial term and at most 14 days per renewal.
-- Subtracting the maximum old term yields a conservative lower-bound start date:
-- shorter requested renewals make this earlier than the original loan date, never later.
UPDATE borrow_transaction
SET loan_started_date = due_date - (30 + renewal_count * 14)
WHERE status = 'LENT' AND due_date IS NOT NULL;

-- Preserve an anchor for older live rows without a due date.
UPDATE borrow_transaction
SET loan_started_date = COALESCE(reservation_date, created_at::date)
WHERE status = 'LENT' AND loan_started_date IS NULL;
