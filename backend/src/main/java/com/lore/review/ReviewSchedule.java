package com.lore.review;

import com.lore.change.Change;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Spaced review. A change that becomes understood (marked by hand, "Skip test", or a
 * teach-back pass) comes back after 1 day; each passed review moves it to the next gap
 * (3, then 7, then 14 days), and passing the 14-day review finishes it. A "partly" answer
 * repeats the same gap; a failed one starts again at 1 day.
 */
@Service
public class ReviewSchedule {

    static final int[] DAYS = {1, 3, 7, 14};

    public enum Result { PASS, PARTLY, FAIL }

    private final ReviewItemRepository repository;
    private final LoreClock clock;

    public ReviewSchedule(ReviewItemRepository repository, LoreClock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Call after a change's understood state was saved. Understood: it joins the queue
     * (if it isn't in it already). Not understood any more: it leaves the queue.
     */
    public void understoodChanged(Change change) {
        Optional<ReviewItem> item = repository.findByChangeId(change.getId());
        if (change.isUnderstood() && item.isEmpty()) {
            Instant now = clock.now();
            repository.save(new ReviewItem(change, now.plus(Duration.ofDays(DAYS[0])), now));
        } else if (!change.isUnderstood()) {
            item.ifPresent(repository::delete);
        }
    }

    /** Moves the item on after a review, as described in the class comment. */
    public ReviewItem record(ReviewItem item, Result result) {
        Instant now = clock.now();
        switch (result) {
            case PASS -> {
                if (item.getStep() == DAYS.length - 1) {
                    item.setDoneAt(now);
                } else {
                    item.setStep(item.getStep() + 1);
                }
            }
            case FAIL -> item.setStep(0);
            case PARTLY -> { }
        }
        item.setDueAt(now.plus(Duration.ofDays(DAYS[item.getStep()])));
        return repository.save(item);
    }

    /** The gap, in days, the item is on now (1, 3, 7 or 14). */
    public static int gapDays(ReviewItem item) {
        return DAYS[item.getStep()];
    }

    /** True if it's waiting to be reviewed now: due, and not finished. */
    public boolean isDue(ReviewItem item) {
        return item.getDoneAt() == null && !item.getDueAt().isAfter(clock.now());
    }
}
