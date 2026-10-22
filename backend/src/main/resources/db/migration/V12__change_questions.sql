-- Follow-up questions asked about a change under its explanation, with the AI's answer.
CREATE TABLE change_questions (
    id UUID PRIMARY KEY,
    change_id UUID NOT NULL REFERENCES changes(id),
    question TEXT NOT NULL,
    answer TEXT NOT NULL,
    model VARCHAR(100),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_change_questions_change_id ON change_questions(change_id);
