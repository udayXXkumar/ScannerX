CREATE TABLE scan_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_id BIGINT NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    scan_profile VARCHAR(50),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_run_at TIMESTAMP NULL,
    next_run_at TIMESTAMP NULL,
    FOREIGN KEY (target_id) REFERENCES targets(id) ON DELETE CASCADE
);
