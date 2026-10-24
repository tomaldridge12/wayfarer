package dev.wayfarer.curios;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Supply rolls and bait: plain rules, no server types. */
public final class SupplyRules {
    /** One weighted line of a loot supply: an item and how many (from min to max). */
    public record Entry(String item, int min, int max, int weight) {
    }

    /** At most this many baited casts can be stored up. */
    public static final int MAX_BAIT = 64;

    private SupplyRules() {
    }

    /** {@code rolls} weighted picks (repeats allowed), merged by item in the order first rolled. */
    public static Map<String, Integer> roll(List<Entry> loot, int rolls, RandomGenerator random) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int total = loot.stream().mapToInt(Entry::weight).sum();
        for (int i = 0; i < rolls && total > 0; i++) {
            int pick = random.nextInt(total);
            for (Entry e : loot) {
                pick -= e.weight();
                if (pick < 0) {
                    out.merge(e.item(), e.min() + (e.max() > e.min() ? random.nextInt(e.max() - e.min() + 1) : 0), Integer::sum);
                    break;
                }
            }
        }
        return out;
    }

    /** Bait added to what is stored, capped. */
    public static int addBait(int stored, int casts) {
        return Math.min(MAX_BAIT, stored + casts);
    }

    /** What one cast uses: the rare-odds factor (2 when baited) and the bait left. */
    public record Cast(double factor, int left) {
    }

    public static Cast cast(int stored) {
        return stored > 0 ? new Cast(2, stored - 1) : new Cast(1, 0);
    }
}
