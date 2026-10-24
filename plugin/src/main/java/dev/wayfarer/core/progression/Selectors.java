package dev.wayfarer.core.progression;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Ladder selectors as config writes them: {@code verb:target} exactly, {@code verb:#namespace:tag} expanded to one
 * entry per key in the tag, and {@code verb:*} or {@code verb:prefix*} kept as a prefix. No server types.
 */
public final class Selectors {
    private Selectors() {
    }

    /**
     * The activity keys of a config list (or a single string); bad entries and unknown tags are left out with a
     * problem. Empty when nothing usable was listed.
     *
     * @param tags the keys in a block or item tag ({@code minecraft:logs}), empty when the server has no such tag
     */
    public static Set<String> parse(Object value, Function<String, Set<String>> tags, Consumer<String> problems,
            String where) {
        Set<String> out = new LinkedHashSet<>();
        for (Object o : value instanceof List<?> list ? list : value == null ? List.of() : List.of(value)) {
            String entry = String.valueOf(o).trim().toLowerCase(Locale.ROOT);
            int cut = entry.indexOf(':');
            if (cut <= 0 || cut == entry.length() - 1) {
                problems.accept(where + ": counts entry '" + o + "' is not verb:target; left out");
                continue;
            }
            String verb = entry.substring(0, cut);
            String target = entry.substring(cut + 1);
            if (target.startsWith("#")) {
                Set<String> keys = tags.apply(target.substring(1));
                if (keys.isEmpty()) {
                    problems.accept(where + ": tag " + target + " is empty or unknown; left out");
                }
                keys.forEach(k -> out.add(verb + ":" + k));
            } else {
                out.add(entry);
            }
        }
        return Set.copyOf(out);
    }
}
