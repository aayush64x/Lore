-- Older builds saved "AI not available" placeholder text as if it were a real
-- summary or explanation. Mark those as FAILED so they can be retried.
UPDATE sessions
SET summary = NULL, summary_status = 'FAILED'
WHERE summary LIKE 'AI summary not available%';

UPDATE explanations
SET overview = NULL, steps = NULL, concepts = NULL, status = 'FAILED'
WHERE overview LIKE 'AI explanation not available%';

-- The placeholder per-change description was "<CHANGE_TYPE> <file_path>".
UPDATE changes
SET description = NULL
WHERE description = change_type || ' ' || file_path;
