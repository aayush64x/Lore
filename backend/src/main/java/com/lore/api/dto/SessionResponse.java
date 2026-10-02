package com.lore.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String reason,
        String summary,
        String summaryStatus,
        Instant startedAt,
        Instant stoppedAt,
        int fileCount,
        int changeCount,
        int understoodCount
) {}