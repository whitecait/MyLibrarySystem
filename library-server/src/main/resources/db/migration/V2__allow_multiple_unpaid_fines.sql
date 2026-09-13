-- BR-0018 checks whether ANY unpaid fine exists; it does not limit their count.
-- Separate overdue returns may each create a fine. Keep UNIQUE(source_tx_id)
-- to reject duplicate fines for the same return, preserving existing data.
DROP INDEX uq_fine_open_per_reader;
CREATE INDEX ix_fine_due_per_reader ON fine (reader_id) WHERE status = 'DUE';
