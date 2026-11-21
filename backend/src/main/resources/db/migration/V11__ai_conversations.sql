CREATE TABLE ai_conversation (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_ai_conversation_updated (updated_at)
);

CREATE TABLE ai_message (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    content LONGTEXT NOT NULL,
    error_message VARCHAR(2000),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_ai_message_conversation (conversation_id, created_at, id),
    FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id) ON DELETE CASCADE
);

CREATE TABLE ai_task (
    id VARCHAR(36) PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    user_message_id VARCHAR(36) NOT NULL,
    assistant_message_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    lease_token VARCHAR(36),
    lease_expires_at DATETIME(6),
    posting_context_json LONGTEXT NOT NULL,
    search_performed BIT NOT NULL DEFAULT 0,
    last_event_id BIGINT NOT NULL DEFAULT 0,
    error_message LONGTEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_ai_task_claim (status, created_at),
    INDEX idx_ai_task_lease (status, lease_expires_at),
    UNIQUE KEY uk_ai_task_assistant_message (assistant_message_id),
    FOREIGN KEY (conversation_id) REFERENCES ai_conversation(id) ON DELETE CASCADE,
    FOREIGN KEY (user_message_id) REFERENCES ai_message(id) ON DELETE CASCADE,
    FOREIGN KEY (assistant_message_id) REFERENCES ai_message(id) ON DELETE CASCADE
);

CREATE TABLE ai_task_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL,
    event_type VARCHAR(24) NOT NULL,
    payload_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_ai_task_event_replay (task_id, id),
    FOREIGN KEY (task_id) REFERENCES ai_task(id) ON DELETE CASCADE
);

CREATE TABLE ai_citation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    source_type VARCHAR(24) NOT NULL,
    posting_id BIGINT,
    version_id BIGINT,
    company VARCHAR(255),
    title VARCHAR(1000) NOT NULL,
    url VARCHAR(2048),
    reason VARCHAR(1000),
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_ai_citation_position (message_id, position),
    INDEX idx_ai_citation_posting (posting_id),
    FOREIGN KEY (message_id) REFERENCES ai_message(id) ON DELETE CASCADE
);
