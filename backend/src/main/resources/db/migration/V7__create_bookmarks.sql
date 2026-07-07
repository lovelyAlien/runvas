CREATE TABLE bookmarks (
    user_id    VARCHAR(36) NOT NULL,
    course_id  VARCHAR(36) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, course_id),
    FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
);
