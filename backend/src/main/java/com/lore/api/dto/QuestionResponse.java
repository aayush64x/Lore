package com.lore.api.dto;

import java.time.Instant;

/** A follow-up question asked about a change, and its answer. */
public record QuestionResponse(String question, String answer, Instant createdAt) {}
