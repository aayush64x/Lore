-- The concepts a change was tagged with during the summary: names from concepts.txt, or,
-- when nothing on the list fit, one name the AI suggested (other = TRUE), kept apart for review.
CREATE TABLE change_concepts (
    id UUID PRIMARY KEY,
    change_id UUID NOT NULL REFERENCES changes(id),
    name VARCHAR(100) NOT NULL,
    other BOOLEAN NOT NULL
);

CREATE INDEX idx_change_concepts_change_id ON change_concepts(change_id);
