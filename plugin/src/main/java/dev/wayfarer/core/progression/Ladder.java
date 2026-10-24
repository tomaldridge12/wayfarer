package dev.wayfarer.core.progression;

import java.util.List;
import java.util.Set;

/**
 * Rewards in order along a set of counters, summed when read: a mob family's banner, trophy head and relics
 * along every {@code kill:<type>} in the family. A selector entry ending in {@code *} is a prefix ({@code catch:*}
 * reads every catch). {@code unit} names one step ("kill") for progress text. A {@code distinct} ladder counts how
 * many selected activities are above zero instead (species caught, biomes discovered), for collections.
 */
public record Ladder(String id, String title, String unit, Set<String> selector, List<Rung> rungs, boolean announce,
        boolean distinct) {
    /**
     * One reward and the summed count that earns it. {@code announce} overrides the ladder's server-wide announcement
     * for this rung (a mark announces only its top rank); null follows the ladder.
     */
    public record Rung(long threshold, String reward, Boolean announce) {
        /** A rung no count reaches: the reward only comes from a drop, and shows once earned. */
        public static final long NEVER = Long.MAX_VALUE;

        public Rung(long threshold, String reward) {
            this(threshold, reward, null);
        }
    }

    /** Whether earning this reward is announced server-wide. */
    public boolean announces(String reward) {
        return rungs.stream().filter(r -> r.reward().equals(reward)).findFirst()
                .map(r -> r.announce() != null ? r.announce() : announce).orElse(announce);
    }

    public Ladder {
        selector = Set.copyOf(selector);
        rungs = rungs.stream().sorted(java.util.Comparator.comparingLong(Rung::threshold)).toList();
    }

    /** A summing ladder. */
    public Ladder(String id, String title, String unit, Set<String> selector, List<Rung> rungs, boolean announce) {
        this(id, title, unit, selector, rungs, announce, false);
    }

    /**
     * The player's position on this ladder: the sum of the selected counters, or for a distinct ladder how many of
     * them are above zero.
     */
    public long progress(ProgressionData data) {
        long sum = 0;
        List<String> prefixes = new java.util.ArrayList<>();
        for (String entry : selector) {
            if (isPrefix(entry)) {
                prefixes.add(entry.substring(0, entry.length() - 1));
            } else {
                sum += value(data.count(entry));
            }
        }
        if (!prefixes.isEmpty()) {
            for (var e : data.countersView().entrySet()) {
                // An activity both listed exactly and under a prefix is still counted once.
                if (!selector.contains(e.getKey()) && prefixes.stream().anyMatch(e.getKey()::startsWith)) {
                    sum += value(e.getValue());
                }
            }
        }
        return sum;
    }

    private long value(long count) {
        return distinct ? (count > 0 ? 1 : 0) : count;
    }

    /** Whether this ladder reads the activity, exactly or by a prefix entry. */
    public boolean reads(String activity) {
        if (selector.contains(activity)) {
            return true;
        }
        for (String entry : selector) {
            if (isPrefix(entry) && activity.startsWith(entry.substring(0, entry.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    static boolean isPrefix(String entry) {
        return entry.endsWith("*");
    }
}
