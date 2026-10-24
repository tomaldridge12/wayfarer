package dev.wayfarer.collections;

import dev.wayfarer.core.progression.Ladder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Collection maths: milestone thresholds, the distinct ladder, and what a player has found. No server types. */
public final class CollectionRules {
    private CollectionRules() {
    }

    /** A milestone's count for a collection of this size: a share rounds up, a count is capped at the size; at least 1. */
    public static long threshold(CollectionDefinition.Milestone m, int size) {
        if (m.share()) {
            double pct = Double.parseDouble(m.at().substring(0, m.at().length() - 1));
            return Math.max(1, (long) Math.ceil(size * pct / 100.0 - 1e-9));
        }
        return Math.max(1, Math.min(Long.parseLong(m.at()), size));
    }

    /** The distinct ladder counting a collection: one rung per milestone reward, and only completion is announced. */
    public static Ladder ladder(CollectionDefinition def, List<CollectionEntry> entries) {
        List<Ladder.Rung> rungs = new ArrayList<>();
        for (CollectionDefinition.Milestone m : def.milestones()) {
            long at = threshold(m, entries.size());
            boolean complete = at >= entries.size();
            m.rewards().forEach(r -> rungs.add(new Ladder.Rung(at, r, complete)));
        }
        return new Ladder(def.ladderId(), def.title(), def.unit(),
                entries.stream().map(CollectionEntry::activity).collect(java.util.stream.Collectors.toSet()),
                rungs, false, true);
    }

    /** How many times the entry's activity was done (a group sums its variants); above zero means found. */
    public static long count(CollectionEntry entry, Map<String, Long> counters) {
        long n = 0;
        for (String a : Ladder.alternatives(entry.activity())) {
            n += counters.getOrDefault(a, 0L);
        }
        return n;
    }

    public static long found(List<CollectionEntry> entries, Map<String, Long> counters) {
        return entries.stream().filter(e -> count(e, counters) > 0).count();
    }

    /** A bar of {@code width} cells, filled in proportion: "▰▰▰▱▱". */
    public static String bar(long done, long total, int width) {
        int filled = total <= 0 ? 0 : (int) Math.min(width, Math.round(width * (double) done / total));
        return "▰".repeat(filled) + "▱".repeat(width - filled);
    }

    /** "Bone Dice" style title-case of a key, ignoring a namespace: "terralith:alpine_grove" gives "Alpine Grove". */
    public static String title(String key) {
        String path = key.substring(key.indexOf(':') + 1);
        path = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (String w : path.split("_")) {
            if (!w.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return out.toString();
    }
}
