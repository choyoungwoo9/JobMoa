CREATE TABLE posting_observation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    posting_id BIGINT NOT NULL,
    task_id VARCHAR(36),
    observed_at DATETIME(6) NOT NULL,
    closed BIT NOT NULL,
    baseline BIT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_observation_task (task_id),
    INDEX idx_observation_posting_time (posting_id, observed_at, id),
    INDEX idx_observation_time (observed_at),
    FOREIGN KEY (posting_id) REFERENCES posting(id),
    FOREIGN KEY (task_id) REFERENCES crawl_task(id)
);

-- 기존 공고는 마지막 확인 상태만 안다. 과거 마감일과 재오픈 이력을 추정하지 않는다.
INSERT INTO posting_observation (posting_id, observed_at, closed, baseline)
SELECT id, last_seen, closed, b'1' FROM posting;
