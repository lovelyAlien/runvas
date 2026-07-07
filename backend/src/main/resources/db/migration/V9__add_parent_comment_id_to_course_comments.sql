ALTER TABLE course_comments
    ADD COLUMN parent_comment_id VARCHAR(36) REFERENCES course_comments (id) ON DELETE CASCADE;

CREATE INDEX idx_course_comments_parent_comment_id ON course_comments (parent_comment_id);
