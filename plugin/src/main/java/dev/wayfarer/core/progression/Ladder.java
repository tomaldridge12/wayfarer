package dev.wayfarer.core.progression;

import java.util.List;
import java.util.Set;

/**
 * Rewards in order along a set of counters, summed when read: a mob family's banner, trophy head and relics
 * along every {@code kill:<type>} in the family. {@code unit} names one step ("kill") for progress text.
 */
public record Ladder(String id, String title, String unit, Set<String> selector, List<Rung> rungs, boolean announce) {
    /** One reward and the summed count that earns it. */
    public record Rung(long threshold, String reward) {
        /** A rung no count reaches: the reward only comes from a drop, and shows once earned. */
        public static final long NEVER = Long.MAX_VALUE;
    }

    public Ladder {
        selector = Set.copyOf(selector);
        rungs = rungs.stream().sorted(java.util.Comparator.comparingLong(Rung::threshold)).toList();
    }

    /** The player's position on this ladder: the sum of the selected counters. */
    public long progress(ProgressionData data) {
        long sum = 0;
        for (String activity : selector) {
            sum += data.count(activity);
        }
        return sum;
    }
}
