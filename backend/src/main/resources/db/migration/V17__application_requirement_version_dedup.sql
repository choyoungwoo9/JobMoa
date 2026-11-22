ALTER TABLE posting_application_requirement
    ADD COLUMN version_saved BIT NOT NULL DEFAULT b'0' AFTER public_page_fetched,
    ADD COLUMN content_version_id VARCHAR(36) NULL AFTER version_saved,
    ADD INDEX idx_application_requirement_versions (posting_id, version_saved, created_at, id);

UPDATE posting_application_requirement
SET version_saved = CASE
        WHEN source IN ('PUBLIC_PAGE', 'USER_PASTED')
            AND status = 'SAVED'
            AND CHAR_LENGTH(TRIM(raw_text)) > 0 THEN b'1'
        ELSE b'0'
    END,
    content_version_id = CASE
        WHEN source IN ('PUBLIC_PAGE', 'USER_PASTED')
            AND status = 'SAVED'
            AND CHAR_LENGTH(TRIM(raw_text)) > 0 THEN id
        ELSE NULL
    END;
