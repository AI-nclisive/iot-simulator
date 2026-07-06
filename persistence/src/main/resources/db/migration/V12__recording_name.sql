-- IS-144: user-supplied name for recordings
ALTER TABLE recordings ADD COLUMN name VARCHAR(255) NULL;
