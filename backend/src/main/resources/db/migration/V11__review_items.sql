-- Spaced review: one row per understood change. step is how far along it is
-- (0 = 1 day, 1 = 3 days, 2 = 7 days, 3 = 14 days); due_at is when it next comes back.
-- done_at is set once the 14-day review is passed (it doesn't come back after that).
CREATE TABLE review_items (
    id UUID PRIMARY KEY,
    change_id UUID NOT NULL UNIQUE REFERENCES changes(id),
    step INT NOT NULL,
    due_at TIMESTAMP NOT NULL,
    done_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_review_items_due_at ON review_items(due_at);

-- Changes already understood join the queue too, due a day from now.
INSERT INTO review_items (id, change_id, step, due_at, created_at)
SELECT RANDOM_UUID(), id, 0, DATEADD('DAY', 1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP
FROM changes WHERE understood = TRUE;
