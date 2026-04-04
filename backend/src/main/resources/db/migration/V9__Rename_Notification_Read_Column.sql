SET @has_read_column := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'notifications'
      AND COLUMN_NAME = 'read'
);

SET @rename_sql := IF(
    @has_read_column > 0,
    'ALTER TABLE notifications CHANGE COLUMN `read` is_read BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1'
);

PREPARE rename_statement FROM @rename_sql;
EXECUTE rename_statement;
DEALLOCATE PREPARE rename_statement;
