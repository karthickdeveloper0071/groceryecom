-- Optimistic locking for every entity extending BaseEntity (shared/persistence/BaseEntity.java).
-- Two transactions writing the same row can no longer overwrite each other silently:
-- the second commit fails and the API answers 409 CONCURRENT_MODIFICATION.
-- Every new table must include this column.
ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
