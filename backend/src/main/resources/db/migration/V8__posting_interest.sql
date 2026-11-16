CREATE TABLE posting_interest (
    posting_id BIGINT PRIMARY KEY,
    starred BIT NOT NULL DEFAULT 0,
    note TEXT NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'NONE',
    application_status VARCHAR(32) NOT NULL DEFAULT 'BEFORE_APPLY',
    applied_on DATE,
    updated_at DATETIME(6) NOT NULL,
    INDEX idx_interest_status (starred, application_status),
    CONSTRAINT fk_interest_posting FOREIGN KEY (posting_id) REFERENCES posting(id),
    CONSTRAINT ck_interest_application CHECK (
        (application_status = 'BEFORE_APPLY' AND applied_on IS NULL)
        OR (application_status = 'APPLIED' AND applied_on IS NOT NULL)
    )
);
