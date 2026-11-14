CREATE TABLE collection_run (
    id VARCHAR(36) PRIMARY KEY,
    started_at DATETIME(6) NOT NULL,
    status VARCHAR(32) NOT NULL,
    finished_at DATETIME(6),
    retry_of VARCHAR(36),
    INDEX idx_run_started (started_at)
);
CREATE TABLE collection_lock (
    id VARCHAR(32) PRIMARY KEY,
    active_run_id VARCHAR(36),
    FOREIGN KEY (active_run_id) REFERENCES collection_run(id)
);
INSERT INTO collection_lock (id) VALUES ('collection');
CREATE TABLE collection_run_source (
    id VARCHAR(80) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    error LONGTEXT,
    failed_at DATETIME(6),
    retry_count INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_run_source (run_id, source_id),
    FOREIGN KEY (run_id) REFERENCES collection_run(id)
);
CREATE TABLE crawl_task (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    source_id VARCHAR(64) NOT NULL,
    company VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    url_hash VARCHAR(64) NOT NULL,
    site_key VARCHAR(255) NOT NULL,
    discovered_at DATETIME(6) NOT NULL,
    title VARCHAR(1000) NOT NULL,
    location VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    lease_token VARCHAR(36),
    lease_expires_at DATETIME(6),
    body_text LONGTEXT,
    content_hash VARCHAR(64),
    capture_path VARCHAR(500),
    error LONGTEXT,
    failed_at DATETIME(6),
    outcome VARCHAR(32),
    retry_of_task_id VARCHAR(36),
    resolved_at DATETIME(6),
    closed BIT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_task_run_url (run_id, url_hash),
    INDEX idx_task_claim (status, site_key, discovered_at),
    INDEX idx_task_lease (status, lease_expires_at),
    FOREIGN KEY (run_id) REFERENCES collection_run(id)
);
CREATE TABLE posting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id VARCHAR(64) NOT NULL,
    company VARCHAR(255) NOT NULL,
    title VARCHAR(1000) NOT NULL,
    location VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    url_hash VARCHAR(64) NOT NULL,
    first_seen DATETIME(6) NOT NULL,
    last_seen DATETIME(6) NOT NULL,
    is_read BIT NOT NULL DEFAULT 0,
    closed BIT NOT NULL DEFAULT 0,
    last_change VARCHAR(32),
    UNIQUE KEY uk_posting_url (url_hash),
    INDEX idx_posting_first_seen (first_seen)
);
CREATE TABLE posting_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    posting_id BIGINT NOT NULL,
    task_id VARCHAR(36) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    body_text LONGTEXT NOT NULL,
    capture_path VARCHAR(500) NOT NULL,
    saved_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_version_task (task_id),
    INDEX idx_version_posting (posting_id, saved_at),
    FOREIGN KEY (posting_id) REFERENCES posting(id),
    FOREIGN KEY (task_id) REFERENCES crawl_task(id)
);
