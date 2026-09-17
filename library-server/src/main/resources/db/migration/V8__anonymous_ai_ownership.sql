-- B5 anonymous AI ownership and durable event/quota facts. V1 is immutable.
ALTER TABLE ai_session ALTER COLUMN account_id DROP NOT NULL;
ALTER TABLE ai_session ADD COLUMN visitor_owner_digest VARCHAR(64);
ALTER TABLE ai_session ADD CONSTRAINT ck_ai_session_exactly_one_owner CHECK (
    (account_id IS NOT NULL AND visitor_owner_digest IS NULL)
 OR (account_id IS NULL AND visitor_owner_digest IS NOT NULL)
);
ALTER TABLE ai_session ADD CONSTRAINT ck_ai_session_visitor_digest CHECK (
    visitor_owner_digest IS NULL OR visitor_owner_digest ~ '^[0-9a-f]{64}$'
);
CREATE INDEX ix_ai_session_account_owner ON ai_session(account_id) WHERE account_id IS NOT NULL;
CREATE INDEX ix_ai_session_visitor_owner ON ai_session(visitor_owner_digest) WHERE visitor_owner_digest IS NOT NULL;

CREATE TABLE ai_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id BIGINT NOT NULL REFERENCES ai_session(id),
    event_type VARCHAR(40) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_ai_event_session_page ON ai_event(session_id,id);

CREATE TABLE ai_visitor_quota_usage (
    usage_date DATE PRIMARY KEY,
    model_calls INT NOT NULL DEFAULT 0 CHECK (model_calls >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Existing migrations define the mandatory fail-closed GLOBAL control row but
-- do not seed it on a fresh database. Preserve an existing row and make a new
-- V8 installation operational without weakening the closing gate.
INSERT INTO closing_control(scope,status) VALUES ('GLOBAL','OPEN')
ON CONFLICT(scope) DO NOTHING;
INSERT INTO closing_admission(scope,draining)
SELECT 'GLOBAL',false WHERE NOT EXISTS (SELECT 1 FROM closing_admission WHERE scope='GLOBAL');
