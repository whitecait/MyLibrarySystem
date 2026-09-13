-- Stop issuing new permits without updating the control row held FOR SHARE
-- by already admitted writers. The isolation commit on closing_control
-- remains logical T; this intent alone does not claim isolation completed.
CREATE TABLE closing_admission (
    scope VARCHAR(32) PRIMARY KEY REFERENCES closing_control(scope),
    draining BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO closing_admission (scope)
SELECT scope FROM closing_control;
