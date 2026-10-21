package com.lore.api.dto;

import java.util.Map;
import java.util.UUID;

public record ChangeResponse(
        UUID id,
        String filePath,
        String changeType,
        String oldFilePath,
        int oldStart,
        int oldCount,
        int newStart,
        int newCount,
        String diffContent,
        String description,
        boolean binary,
        boolean understood,
        int displayOrder,
        // The saved explanation (same shape as POST /changes/{id}/explain), or null.
        Map<String, Object> explanation,
        // "yes" / "partly" / "no" against the goal given at lore stop; null if not checked.
        String goalMatch,
        String goalReason,
        // "teach_back" (passed) / "self" (marked by hand or skipped); null while not understood.
        String understoodBy,
        // attemptsUsed, attemptsLeft, lastVerdict, keyPoints (null until revealed).
        Map<String, Object> teachBack
) {}