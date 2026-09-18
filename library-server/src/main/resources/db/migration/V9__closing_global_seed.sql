-- Closing owns its mandatory fail-closed GLOBAL gate baseline.
-- Forward-only and idempotent: never overwrite an existing operational state.
INSERT INTO closing_control(scope,status) VALUES ('GLOBAL','OPEN')
ON CONFLICT(scope) DO NOTHING;

INSERT INTO closing_admission(scope,draining)
SELECT 'GLOBAL',false FROM closing_control WHERE scope='GLOBAL'
ON CONFLICT(scope) DO NOTHING;
