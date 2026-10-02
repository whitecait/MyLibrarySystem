-- Keep same-day registration and normal release out of the lending path while allowing
-- same-day shelving/allocation. RESTORE writes today's business date in the application.
ALTER TABLE book_copy
    ADD COLUMN loan_eligible_on DATE NOT NULL
    DEFAULT ((CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Shanghai')::date);

-- Current-day PENDING rows are known normal releases. Current-day SHELF_READY/RESERVED
-- rows have no state history to distinguish normal release from RESTORE; the approved
-- legacy policy defers these ambiguous rows by one day. Older rows stay eligible on the
-- migration business date instead of being delayed another day.
UPDATE book_copy
SET loan_eligible_on = (updated_at AT TIME ZONE 'Asia/Shanghai')::date + 1
WHERE status IN ('PENDING_SHELVING', 'SHELF_READY', 'RESERVED')
  AND (updated_at AT TIME ZONE 'Asia/Shanghai')::date =
      (CURRENT_TIMESTAMP AT TIME ZONE 'Asia/Shanghai')::date;
