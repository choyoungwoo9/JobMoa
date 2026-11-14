ALTER TABLE collection_run_source
    ADD COLUMN retry_of_source_id VARCHAR(80),
    ADD COLUMN resolved_at DATETIME(6);
