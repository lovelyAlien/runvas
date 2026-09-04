ALTER TABLE users
    ADD COLUMN terms_agreed_at TIMESTAMPTZ,
    ADD COLUMN banned_at TIMESTAMPTZ;
