ALTER TABLE findings ADD COLUMN ai_description TEXT;
ALTER TABLE findings ADD COLUMN exploit_narrative TEXT;
ALTER TABLE findings ADD COLUMN ai_enrichment_status VARCHAR(32);
ALTER TABLE findings ADD COLUMN ai_model VARCHAR(255);
ALTER TABLE findings ADD COLUMN ai_prompt_fingerprint VARCHAR(255);
ALTER TABLE findings ADD COLUMN ai_enriched_at TIMESTAMP;
ALTER TABLE findings ADD COLUMN ai_enrichment_error TEXT;
