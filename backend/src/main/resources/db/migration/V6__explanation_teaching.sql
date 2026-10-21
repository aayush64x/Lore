-- For teach-back and the "Lore found an issue" badge. NULL on explanations made
-- before this; those show "Explain again to enable testing" and no badge.
-- key_points: JSON list of 2-3 points a person must say to show they understand.
-- testable: FALSE for changes with nothing to understand (imports, formatting, renames).
-- possible_issue: JSON {"diff_line": n, "text": "..."} for a real edge case or bug, or NULL.
ALTER TABLE explanations ADD COLUMN key_points TEXT;
ALTER TABLE explanations ADD COLUMN testable BOOLEAN;
ALTER TABLE explanations ADD COLUMN possible_issue TEXT;
