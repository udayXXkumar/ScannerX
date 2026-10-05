ALTER TABLE findings ADD COLUMN ai_severity VARCHAR(32) NULL;
ALTER TABLE findings ADD COLUMN ai_severity_reason TEXT NULL;
ALTER TABLE findings ADD COLUMN ai_priority_score INT NULL;
ALTER TABLE findings ADD COLUMN ai_priority_reason TEXT NULL;
ALTER TABLE findings ADD COLUMN ai_duplicate_of_id BIGINT NULL;
ALTER TABLE findings ADD CONSTRAINT fk_finding_duplicate FOREIGN KEY (ai_duplicate_of_id) REFERENCES findings(id) ON DELETE SET NULL;
