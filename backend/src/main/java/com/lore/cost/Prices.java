package com.lore.cost;

import com.lore.ai.ApiKeyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Dollars per million tokens for each Claude model, used to estimate what a session cost.
 *
 * Check prices at anthropic.com/pricing. The defaults below were copied from the pricing
 * page (platform.claude.com/docs/en/about-claude/pricing) on Oct 22, 2026. The keys are the
 * exact model names used in application.yml and config.properties. Lore only writes to the
 * 5-minute cache, so cacheWrite is the 5-minute price.
 *
 * Any price can be changed without a rebuild, with lines in ~/.lore/config.properties:
 *   price.claude-haiku-5-5.input=0.10
 *   price.claude-haiku-5-5.output=0.50
 *   price.claude-haiku-5-5.cache-read=0.01
 *   price.claude-haiku-5-5.cache-write=0.125
 * A model that isn't listed here needs all four lines. A model with no price, or with a
 * line that isn't a number, has no price at all: its tokens are shown instead of a guess.
 */
@Component
public class Prices {

    private static final Logger log = LoggerFactory.getLogger(Prices.class);

    public record Price(BigDecimal input, BigDecimal output, BigDecimal cacheRead, BigDecimal cacheWrite) {
        Price(String input, String output, String cacheRead, String cacheWrite) {
            this(new BigDecimal(input), new BigDecimal(output), new BigDecimal(cacheRead), new BigDecimal(cacheWrite));
        }
    }

    static final Map<String, Price> DEFAULTS = Map.of(
            "claude-sonnet-5-5", new Price("2.00", "10.00", "0.10", "2.50"),
            "claude-haiku-5-5", new Price("0.10", "0.50", "0.01", "0.125"),
            "claude-opus-5-5", new Price("4.00", "20.00", "0.20", "5.00"));

    private final ApiKeyStore settings;

    public Prices(ApiKeyStore settings) {
        this.settings = settings;
    }

    /** The model's prices, with any config.properties lines applied; empty if not all known. */
    public Optional<Price> forModel(String model) {
        Price fallback = DEFAULTS.get(model);
        BigDecimal input = part(model, "input", fallback == null ? null : fallback.input());
        BigDecimal output = part(model, "output", fallback == null ? null : fallback.output());
        BigDecimal cacheRead = part(model, "cache-read", fallback == null ? null : fallback.cacheRead());
        BigDecimal cacheWrite = part(model, "cache-write", fallback == null ? null : fallback.cacheWrite());
        if (input == null || output == null || cacheRead == null || cacheWrite == null) return Optional.empty();
        return Optional.of(new Price(input, output, cacheRead, cacheWrite));
    }

    /** One price: the config.properties line if set, else the default. Null if neither, or not a number. */
    private BigDecimal part(String model, String name, BigDecimal fallback) {
        Optional<String> line = settings.setting("price." + model + "." + name);
        if (line.isEmpty()) return fallback;
        try {
            BigDecimal value = new BigDecimal(line.get());
            if (value.signum() >= 0) return value;
        } catch (NumberFormatException e) {
            // Falls through: a bad line means no price, never a guessed one.
        }
        log.warn("price.{}.{} in config.properties isn't a price ({}); showing tokens for {} instead.",
                model, name, line.get(), model);
        return null;
    }
}
