ALTER TABLE users
    ADD COLUMN auth_provider VARCHAR(24) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN google_subject VARCHAR(255) NULL,
    ADD COLUMN avatar_url VARCHAR(512) NULL;

UPDATE users
SET auth_provider = 'LOCAL'
WHERE auth_provider IS NULL OR TRIM(auth_provider) = '';

CREATE UNIQUE INDEX uk_users_google_subject ON users (google_subject);
