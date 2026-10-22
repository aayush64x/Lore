package com.lore.api.dto;

import com.lore.cost.SessionCost;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionDetailResponse(
        UUID id,
        String status,
        String projectPath,
        String reason,
        String summary,
        String summaryStatus,
        Instant startedAt,
        Instant stoppedAt,
        List<ChangeResponse> changes,
        // Parts of the goal no change did; empty = all done; null = not checked.
        List<String> goalGaps,
        // Estimated AI cost so far; null for older sessions and for providers other than Anthropic.
        SessionCost.Cost cost
) {}
