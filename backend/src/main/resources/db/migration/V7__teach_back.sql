-- How a change became understood: TEACH_BACK (passed a teach-back) or SELF (marked by
-- hand or skipped the test). Changes already marked understood count as SELF.
ALTER TABLE changes ADD COLUMN understood_by VARCHAR(20);
UPDATE changes SET understood_by = 'SELF' WHERE understood = TRUE;

-- Every graded teach-back answer. Failed AI calls are not stored (they don't use a try).
CREATE TABLE teach_back_attempts (
    id UUID PRIMARY KEY,
    change_id UUID NOT NULL REFERENCES changes(id),
    answer TEXT NOT NULL,
    verdict VARCHAR(10) NOT NULL,
    got_right TEXT,
    missing TEXT,
    model VARCHAR(100),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_teach_back_attempts_change_id ON teach_back_attempts(change_id);
