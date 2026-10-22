package com.lore.cost;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The estimated AI cost of one session, from its AiCall rows and the price table.
 * Empty when there's nothing to show: a session from before costs were saved, or an AI
 * provider other than the Anthropic API (Bedrock calls aren't recorded; "none" makes none).
 */
@Service
public class SessionCost {

    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    /**
     * dollars is null when a call used a model with no price; tokens is then what to show.
     * jobs always lists summary, explain and grading, in that order (calls = 0 if unused).
     */
    public record Cost(BigDecimal dollars, long tokens, List<JobCost> jobs) {}

    public record JobCost(String job, BigDecimal dollars, long tokens, int calls) {}

    private final AiCallRepository repository;
    private final Prices prices;
    private final String provider;

    public SessionCost(AiCallRepository repository, Prices prices,
                       @Value("${lore.ai.provider:anthropic}") String provider) {
        this.repository = repository;
        this.prices = prices;
        this.provider = provider;
    }

    public Optional<Cost> of(UUID sessionId) {
        if (!"anthropic".equals(provider)) return Optional.empty();
        List<AiCall> calls = repository.findBySessionId(sessionId);
        if (calls.isEmpty()) return Optional.empty();

        List<JobCost> jobs = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        long tokens = 0;
        for (AiCall.Job job : AiCall.Job.values()) {
            List<AiCall> ofJob = calls.stream().filter(c -> c.getJob() == job).toList();
            BigDecimal dollars = dollars(ofJob);
            long jobTokens = ofJob.stream().mapToLong(AiCall::totalTokens).sum();
            jobs.add(new JobCost(job.name().toLowerCase(), dollars, jobTokens, ofJob.size()));
            total = total == null || dollars == null ? null : total.add(dollars);
            tokens += jobTokens;
        }
        return Optional.of(new Cost(total, tokens, jobs));
    }

    /** What these calls cost, or null if any of them used a model with no price. */
    private BigDecimal dollars(List<AiCall> calls) {
        BigDecimal sum = BigDecimal.ZERO;
        for (AiCall c : calls) {
            Optional<Prices.Price> price = prices.forModel(c.getModel());
            if (price.isEmpty()) return null;
            Prices.Price p = price.get();
            sum = sum.add(p.input().multiply(BigDecimal.valueOf(c.getInputTokens())))
                    .add(p.output().multiply(BigDecimal.valueOf(c.getOutputTokens())))
                    .add(p.cacheRead().multiply(BigDecimal.valueOf(c.getCacheReadTokens())))
                    .add(p.cacheWrite().multiply(BigDecimal.valueOf(c.getCacheWriteTokens())));
        }
        return sum.divide(MILLION, 6, RoundingMode.HALF_UP);
    }
}
