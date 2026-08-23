CREATE TABLE reports (
    id VARCHAR(36) PRIMARY KEY,
    reporter_id VARCHAR(36) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    reason VARCHAR(20) NOT NULL,
    reason_detail VARCHAR(200),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX idx_reports_status_created_at ON reports (status, created_at);
CREATE UNIQUE INDEX uq_reports_pending_target
    ON reports (reporter_id, target_type, target_id)
    WHERE status = 'PENDING';
