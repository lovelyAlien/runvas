CREATE TABLE admin_accounts (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX idx_admin_accounts_username ON admin_accounts (username);
