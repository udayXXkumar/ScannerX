ALTER TABLE findings ADD COLUMN ai_description LONGTEXT NULL;
ALTER TABLE findings ADD COLUMN exploit_narrative LONGTEXT NULL;
ALTER TABLE findings ADD COLUMN ai_enrichment_status VARCHAR(32) NULL;
ALTER TABLE findings ADD COLUMN ai_model VARCHAR(255) NULL;
ALTER TABLE findings ADD COLUMN ai_prompt_fingerprint VARCHAR(255) NULL;
ALTER TABLE findings ADD COLUMN ai_enriched_at TIMESTAMP NULL;
ALTER TABLE findings ADD COLUMN ai_enrichment_error LONGTEXT NULL;
