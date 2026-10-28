-- Run once on existing MySQL databases before deploying visit deletion changes.
ALTER TABLE account ADD COLUMN archived_free_generations BIGINT NOT NULL DEFAULT 0;
ALTER TABLE payment_order MODIFY COLUMN visit_id INT NULL;
