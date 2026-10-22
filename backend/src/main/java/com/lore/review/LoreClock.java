package com.lore.review;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * "Now" for reviews. Normally the real time; the demo control can move it forward
 * ("pretend 3 days passed") to show reviews coming due. The shift only lives in this
 * process, so restarting lore ui puts the clock back.
 */
@Component
public class LoreClock {

    private volatile Duration shift = Duration.ZERO;

    public Instant now() {
        return Instant.now().plus(shift);
    }

    public void pretendDaysPassed(int days) {
        shift = shift.plusDays(days);
    }

    public void reset() {
        shift = Duration.ZERO;
    }

    /** Whole days the clock is ahead of real time (0 normally). */
    public long daysAhead() {
        return shift.toDays();
    }
}
