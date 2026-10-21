-- Answers given after a change was already passed are practice: graded the same way,
-- but they don't use tries and never change the change's status.
ALTER TABLE teach_back_attempts ADD COLUMN practice BOOLEAN NOT NULL DEFAULT FALSE;
