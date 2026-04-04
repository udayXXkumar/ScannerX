ALTER TABLE scans
    ADD COLUMN name VARCHAR(255) NULL AFTER target_id;

UPDATE scans s
JOIN targets t ON t.id = s.target_id
SET s.name = CONCAT(
    COALESCE(NULLIF(TRIM(t.name), ''), 'Scan'),
    ' · ',
    DATE_FORMAT(COALESCE(s.created_at, NOW()), '%Y-%m-%d %H:%i')
)
WHERE s.name IS NULL OR TRIM(s.name) = '';

ALTER TABLE scans
    MODIFY COLUMN name VARCHAR(255) NOT NULL;

ALTER TABLE notifications
    ADD COLUMN scan_id BIGINT NULL AFTER message,
    ADD COLUMN target_id BIGINT NULL AFTER scan_id,
    ADD COLUMN finding_count INT NULL AFTER target_id,
    ADD CONSTRAINT fk_notifications_scan FOREIGN KEY (scan_id) REFERENCES scans(id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_notifications_target FOREIGN KEY (target_id) REFERENCES targets(id) ON DELETE CASCADE;

ALTER TABLE targets
    ADD CONSTRAINT uk_targets_user_name UNIQUE (user_id, name);
