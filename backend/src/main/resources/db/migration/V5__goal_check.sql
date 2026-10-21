-- Goal check from the lore stop summary. NULL = not checked (no goal given, older
-- sessions, or a provider without the check); old rows stay as they are.
-- goal_match is YES, PARTLY or NO. goal_gaps is a JSON list of the goal's parts no
-- change did ("[]" = all done).
ALTER TABLE changes ADD COLUMN goal_match VARCHAR(10);
ALTER TABLE changes ADD COLUMN goal_reason VARCHAR(1024);
ALTER TABLE sessions ADD COLUMN goal_gaps TEXT;
