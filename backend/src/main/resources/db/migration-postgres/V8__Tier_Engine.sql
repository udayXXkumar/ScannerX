ALTER TABLE targets
    ADD COLUMN default_tier VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    ADD COLUMN timeouts_enabled BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE scans
    ADD COLUMN tier VARCHAR(32) NULL,
    ADD COLUMN timeouts_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN execution_context_json TEXT NULL,
    ADD COLUMN normalized_report_json TEXT NULL;

UPDATE targets
SET default_tier = 'MEDIUM',
    timeouts_enabled = TRUE
WHERE default_tier IS NULL OR BTRIM(default_tier) = '';

UPDATE scans
SET tier = CASE UPPER(COALESCE(profile_type, 'STANDARD'))
    WHEN 'QUICK' THEN 'FAST'
    WHEN 'COMPREHENSIVE' THEN 'DEEP'
    ELSE 'MEDIUM'
END,
timeouts_enabled = TRUE
WHERE tier IS NULL OR BTRIM(tier) = '';
