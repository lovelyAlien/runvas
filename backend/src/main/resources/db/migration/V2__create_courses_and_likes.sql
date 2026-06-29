CREATE TABLE courses (
    id VARCHAR(36) PRIMARY KEY,
    author_id VARCHAR(36) NOT NULL,
    title VARCHAR(60) NOT NULL,
    description VARCHAR(500),
    path TEXT NOT NULL,
    waypoints TEXT NOT NULL,
    distance_meters INTEGER NOT NULL,
    estimated_duration_seconds INTEGER NOT NULL,
    sw_lat DOUBLE PRECISION NOT NULL,
    sw_lng DOUBLE PRECISION NOT NULL,
    ne_lat DOUBLE PRECISION NOT NULL,
    ne_lng DOUBLE PRECISION NOT NULL,
    visibility VARCHAR(20) NOT NULL,
    like_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE course_tags (
    course_id VARCHAR(36) NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    tag VARCHAR(20) NOT NULL,
    PRIMARY KEY (course_id, tag)
);

CREATE TABLE likes (
    user_id VARCHAR(36) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (user_id, target_type, target_id)
);

CREATE INDEX idx_courses_bounds ON courses (sw_lat, ne_lat, sw_lng, ne_lng);
CREATE INDEX idx_courses_author ON courses (author_id);
