CREATE TABLE course_comments (
    id         VARCHAR(36)  NOT NULL PRIMARY KEY,
    course_id  VARCHAR(36)  NOT NULL,
    author_id  VARCHAR(36)  NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    image_url  VARCHAR(1000),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
);

CREATE INDEX idx_course_comments_course_id_created_at ON course_comments (course_id, created_at DESC);
