ALTER TABLE collection_run ADD COLUMN category_ids TEXT NULL;
UPDATE collection_run SET category_ids=category_id;
ALTER TABLE collection_run MODIFY COLUMN category_ids TEXT NOT NULL;
ALTER TABLE collection_preset CHANGE COLUMN category_id category_ids TEXT NOT NULL;
