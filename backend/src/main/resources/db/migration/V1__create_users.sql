CREATE TABLE users (
    id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    provider_user_id VARCHAR(100) NOT NULL,
    email VARCHAR(320),
    nickname VARCHAR(30) NOT NULL,
    profile_image_url VARCHAR(1000),
    bio VARCHAR(160),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_users_provider_provider_user_id UNIQUE (provider, provider_user_id)
);
