CREATE TABLE withdrawal_feedback (
    id VARCHAR(36) PRIMARY KEY,
    reason_code VARCHAR(30) NOT NULL,
    reason_detail VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
