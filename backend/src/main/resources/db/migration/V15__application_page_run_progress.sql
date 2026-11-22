ALTER TABLE posting_application_requirement
    ADD COLUMN collection_run_id VARCHAR(36) NULL AFTER posting_id,
    ADD COLUMN public_page_fetched BIT NOT NULL DEFAULT 0 AFTER status,
    ADD INDEX idx_application_requirement_run (collection_run_id, created_at),
    ADD UNIQUE INDEX uk_application_requirement_run_posting (collection_run_id, posting_id),
    ADD CONSTRAINT fk_application_requirement_run FOREIGN KEY (collection_run_id) REFERENCES collection_run(id) ON DELETE SET NULL;

UPDATE posting_application_requirement requirement
JOIN posting ON posting.id = requirement.posting_id
SET requirement.public_page_fetched = 1
WHERE requirement.source = 'PUBLIC_PAGE'
  AND CHAR_LENGTH(requirement.raw_text) >= 40
  AND requirement.source_url IS NOT NULL
  AND requirement.source_url <> posting.url;

UPDATE posting_application_requirement
SET status = 'FAILED',
    error_message = '과거 공고 본문 분석은 지원서 항목으로 사용하지 않습니다. 실제 지원 페이지를 다시 확인하거나 문항을 직접 붙여넣어 주세요.',
    lease_token = NULL,
    lease_expires_at = NULL,
    updated_at = CURRENT_TIMESTAMP(6)
WHERE source = 'POSTING_BODY'
  AND status IN ('WAITING_AI', 'ANALYZING');
