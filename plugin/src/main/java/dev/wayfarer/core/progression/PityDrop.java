package dev.wayfarer.core.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A rare drop with a ceiling on bad luck: after {@link #threshold()} counted kills without one, the next is
 * guaranteed. Targets are activity targets ("wither_skeleton"); {@code heldItem} is null when any mob counts.
 */
public record PityDrop(String id, String item, Set<String> mobs, String heldItem, double chance) {
    public PityDrop {
        mobs = Set.copyOf(mobs);
    }

    /** ceil(1 / chance): a 1% drop is guaranteed by the 100th kill. Looting never changes it. */
    public int threshold() {
        return (int) Math.ceil(1 / chance - 1e-9); // the margin keeps binary rounding from adding a kill
    }

    /** Whether a kill of this mob, holding this item (or null), advances this pity counter. */
    boolean applies(String mob, String held) {
        return mobs.contains(mob) && (heldItem == null || heldItem.equals(held));
    }

    /** The {@code pity} section: id → {item, mobs, held-item?, chance}. Bad entries are skipped with a problem. */
    static List<PityDrop> parse(Object raw, ProgressionSettings.Names names, List<String> problems) {
        List<PityDrop> out = new ArrayList<>();
        if (!(raw instanceof Map<?, ?> map)) {
            return out;
        }
        new LinkedHashMap<>(map).forEach((key, value) -> {
            String id = String.valueOf(key);
            String where = "pity." + id + " skipped: ";
            if (!(value instanceof Map<?, ?> m)) {
                problems.add(where + "not a section");
                return;
            }
            String item = lower(m.get("item"));
            String held = lower(m.get("held-item"));
            List<String> mobs = m.get("mobs") instanceof List<?> list ? list.stream().map(PityDrop::lower).toList() : List.of();
            if (item == null || !names.isItem(item)) {
                problems.add(where + "unknown item '" + m.get("item") + "'");
            } else if (held != null && !names.isItem(held)) {
                problems.add(where + "unknown held-item '" + m.get("held-item") + "'");
            } else if (mobs.isEmpty() || !mobs.stream().allMatch(names::isMob)) {
                problems.add(where + "mobs must be a list of known mob types");
            } else if (!(m.get("chance") instanceof Number n) || n.doubleValue() <= 0 || n.doubleValue() > 1) {
                problems.add(where + "chance must be above 0 and at most 1");
            } else {
                out.add(new PityDrop(id, item, Set.copyOf(mobs), held, n.doubleValue()));
            }
        });
        return List.copyOf(out);
    }

    /** A config name as an activity target: lower case, without the minecraft: namespace. */
    private static String lower(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).toLowerCase(Locale.ROOT);
        return s.startsWith("minecraft:") ? s.substring("minecraft:".length()) : s;
    }
}
