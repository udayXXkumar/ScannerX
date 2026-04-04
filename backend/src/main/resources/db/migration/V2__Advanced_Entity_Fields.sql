ALTER TABLE targets ADD COLUMN tags VARCHAR(255);
ALTER TABLE targets ADD COLUMN project_group_name VARCHAR(100);
ALTER TABLE targets ADD COLUMN verification_token VARCHAR(255);

ALTER TABLE findings ADD COLUMN assigned_user VARCHAR(100);
ALTER TABLE findings ADD COLUMN comments TEXT;
ALTER TABLE findings ADD COLUMN evidence_data TEXT;
ALTER TABLE findings ADD COLUMN cwe_id VARCHAR(50);
ALTER TABLE findings ADD COLUMN owasp_category VARCHAR(100);
ALTER TABLE findings ADD COLUMN first_seen_at TIMESTAMP;
ALTER TABLE findings ADD COLUMN last_seen_at TIMESTAMP;
