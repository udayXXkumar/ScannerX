UPDATE scans s
JOIN targets t ON t.id = s.target_id
SET s.name = TRIM(COALESCE(NULLIF(t.name, ''), NULLIF(t.base_url, ''), 'Target'))
WHERE s.target_id IS NOT NULL;
