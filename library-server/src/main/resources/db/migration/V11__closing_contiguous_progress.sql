ALTER TABLE closing_control
    ADD COLUMN first_business_date DATE;

-- Existing batch history establishes its first known point; later close dates
-- must remain contiguous through that point when an administrator reopens.
UPDATE closing_control c
SET first_business_date = (
    SELECT min(b.business_date) FROM closing_batch b
)
WHERE c.scope = 'GLOBAL'
  AND c.first_business_date IS NULL;
