ALTER TABLE targets
    ADD COLUMN default_tier VARCHAR(32) NOT NULL DEFAULT 'MEDIUM' AFTER verification_token,
    ADD COLUMN timeouts_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER default_tier;

ALTER TABLE scans
    ADD COLUMN tier VARCHAR(32) NULL AFTER profile_type,
    ADD COLUMN timeouts_enabled BOOLEAN NOT NULL DEFAULT TRUE AFTER tier,
    ADD COLUMN execution_context_json LONGTEXT NULL AFTER resume_stage_order,
    ADD COLUMN normalized_report_json LONGTEXT NULL AFTER execution_context_json;

UPDATE targets
SET default_tier = 'MEDIUM',
    timeouts_enabled = TRUE
WHERE default_tier IS NULL OR TRIM(default_tier) = '';

UPDATE scans
SET tier = CASE UPPER(COALESCE(profile_type, 'STANDARD'))
    WHEN 'QUICK' THEN 'FAST'
    WHEN 'COMPREHENSIVE' THEN 'DEEP'
    ELSE 'MEDIUM'
END,
timeouts_enabled = TRUE
WHERE tier IS NULL OR TRIM(tier) = '';
