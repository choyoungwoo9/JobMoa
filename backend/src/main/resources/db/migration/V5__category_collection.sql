ALTER TABLE collection_run
    ADD COLUMN category_id VARCHAR(64) NOT NULL DEFAULT 'all',
    ADD COLUMN company_ids TEXT NOT NULL;
ALTER TABLE crawl_task
    ADD COLUMN category_id VARCHAR(64) NOT NULL DEFAULT 'all',
    ADD COLUMN company_id VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN reused_version_id BIGINT NULL;
CREATE TABLE posting_category (
    posting_id BIGINT NOT NULL,
    category_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (posting_id, category_id),
    INDEX idx_posting_category (category_id, posting_id),
    FOREIGN KEY (posting_id) REFERENCES posting(id)
);
