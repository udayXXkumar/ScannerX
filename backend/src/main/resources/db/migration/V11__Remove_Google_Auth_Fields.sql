SET @drop_index_sql = (
    SELECT IF(
        EXISTS(
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'users'
              AND index_name = 'uk_users_google_subject'
        ),
        'DROP INDEX uk_users_google_subject ON users',
        'SELECT 1'
    )
);
PREPARE drop_index_stmt FROM @drop_index_sql;
EXECUTE drop_index_stmt;
DEALLOCATE PREPARE drop_index_stmt;

ALTER TABLE users
    DROP COLUMN avatar_url,
    DROP COLUMN google_subject,
    DROP COLUMN auth_provider;
