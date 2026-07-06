CREATE TABLE posts (
    id VARCHAR(36) PRIMARY KEY,
    author_id VARCHAR(36) NOT NULL,
    title VARCHAR(80) NOT NULL,
    body TEXT NOT NULL,
    attached_course_id VARCHAR(36),
    like_count INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE post_tags (
    post_id VARCHAR(36) NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    tag VARCHAR(20) NOT NULL,
    PRIMARY KEY (post_id, tag)
);

CREATE TABLE comments (
    id VARCHAR(36) PRIMARY KEY,
    post_id VARCHAR(36) NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id VARCHAR(36) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_posts_author ON posts (author_id);
CREATE INDEX idx_posts_attached_course ON posts (attached_course_id);
CREATE INDEX idx_comments_post ON comments (post_id);
