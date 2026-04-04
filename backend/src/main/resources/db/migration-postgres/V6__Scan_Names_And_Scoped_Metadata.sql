ALTER TABLE scans
    ADD COLUMN name VARCHAR(255);

UPDATE scans s
SET name = CONCAT(
    COALESCE(NULLIF(BTRIM(t.name), ''), 'Scan'),
    ' · ',
    TO_CHAR(COALESCE(s.created_at, CURRENT_TIMESTAMP), 'YYYY-MM-DD HH24:MI')
)
FROM targets t
WHERE t.id = s.target_id
  AND (s.name IS NULL OR BTRIM(s.name) = '');

ALTER TABLE scans
    ALTER COLUMN name SET NOT NULL;

ALTER TABLE notifications
    ADD COLUMN scan_id BIGINT NULL,
    ADD COLUMN target_id BIGINT NULL,
    ADD COLUMN finding_count INT NULL,
    ADD CONSTRAINT fk_notifications_scan FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_notifications_target FOREIGN KEY (target_id) REFERENCES targets(id) ON DELETE CASCADE;

ALTER TABLE targets
    ADD CONSTRAINT uk_targets_user_name UNIQUE (user_id, name);
