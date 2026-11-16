CREATE TABLE collection_preset (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    category_id VARCHAR(64) NOT NULL,
    company_ids TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
CREATE TABLE workspace_revision (id VARCHAR(32) PRIMARY KEY, revision VARCHAR(36) NOT NULL);
INSERT INTO workspace_revision VALUES ('workspace', UUID());
CREATE TABLE collection_change (
    task_id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL,
    posting_id BIGINT NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    was_starred BIT NULL,
    became_closed BIT NULL,
    recorded_at DATETIME(6) NOT NULL,
    FOREIGN KEY (task_id) REFERENCES crawl_task(id),
    FOREIGN KEY (run_id) REFERENCES collection_run(id),
    FOREIGN KEY (posting_id) REFERENCES posting(id),
    INDEX idx_change_run (run_id, recorded_at)
);
-- 과거 실행의 신규·변경은 복원하되, 당시 관심 여부와 마감 전환은 추정하지 않는다.
INSERT INTO collection_change(task_id, run_id, posting_id, outcome, recorded_at)
SELECT t.id,t.run_id,p.id,t.outcome,COALESCE(r.finished_at,r.started_at)
FROM crawl_task t JOIN posting p ON p.url_hash=t.url_hash JOIN collection_run r ON r.id=t.run_id
WHERE t.status='COMPLETED' AND t.outcome IN ('NEW','CHANGED','IDENTICAL');
