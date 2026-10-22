-- One row per Claude API call, with the token counts the API sent back, so a session's
-- AI cost can be estimated. job is SUMMARY, EXPLAIN or GRADING. Older sessions have none.
CREATE TABLE ai_calls (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES sessions(id),
    job VARCHAR(20) NOT NULL,
    model VARCHAR(100) NOT NULL,
    input_tokens BIGINT NOT NULL,
    output_tokens BIGINT NOT NULL,
    cache_read_tokens BIGINT NOT NULL,
    cache_write_tokens BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ai_calls_session_id ON ai_calls(session_id);
