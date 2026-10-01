CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    project_path VARCHAR(1024) NOT NULL,
    reason VARCHAR(1024),
    summary TEXT,
    summary_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    start_ref VARCHAR(255) NOT NULL,
    stop_ref VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    stopped_at TIMESTAMP
);

CREATE INDEX idx_sessions_project_path ON sessions(project_path);
CREATE INDEX idx_sessions_status ON sessions(status);

CREATE TABLE changes (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES sessions(id),
    file_path VARCHAR(1024) NOT NULL,
    change_type VARCHAR(20) NOT NULL,
    old_file_path VARCHAR(1024),
    old_start INT NOT NULL DEFAULT 0,
    old_count INT NOT NULL DEFAULT 0,
    new_start INT NOT NULL DEFAULT 0,
    new_count INT NOT NULL DEFAULT 0,
    diff_content TEXT,
    description VARCHAR(2048),
    is_binary BOOLEAN NOT NULL DEFAULT FALSE,
    understood BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_changes_session_id ON changes(session_id);

CREATE TABLE explanations (
    id UUID PRIMARY KEY,
    change_id UUID NOT NULL UNIQUE REFERENCES changes(id),
    overview TEXT,
    steps TEXT,
    concepts TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL
);
