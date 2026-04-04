UPDATE scans s
SET name = BTRIM(COALESCE(NULLIF(t.name, ''), NULLIF(t.base_url, ''), 'Target'))
FROM targets t
WHERE t.id = s.target_id
  AND s.target_id IS NOT NULL;
