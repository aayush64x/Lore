package com.lore.api.dto;

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
        int displayOrder
) {}