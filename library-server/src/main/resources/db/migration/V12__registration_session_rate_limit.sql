CREATE TABLE registration_session_attempt (
    session_hash char(64) PRIMARY KEY,
    window_started_at timestamptz NOT NULL,
    request_count integer NOT NULL CHECK (request_count >= 1),
    updated_at timestamptz NOT NULL
);

CREATE INDEX ix_registration_session_attempt_updated
    ON registration_session_attempt(updated_at);
