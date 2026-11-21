CREATE TABLE career_assistant_task (
    id VARCHAR(36) PRIMARY KEY,
    task_type VARCHAR(32) NOT NULL,
    parent_task_id VARCHAR(36),
    posting_id BIGINT,
    status VARCHAR(24) NOT NULL,
    request_json LONGTEXT NOT NULL,
    result_json LONGTEXT,
    error_message LONGTEXT,
    attempts INT NOT NULL DEFAULT 0,
    lease_token VARCHAR(36),
    lease_expires_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_career_assistant_queue (status, created_at),
    INDEX idx_career_assistant_type (task_type, created_at),
    UNIQUE KEY uk_career_assistant_parent_posting (parent_task_id, posting_id)
);
