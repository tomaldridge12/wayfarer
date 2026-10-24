package dev.wayfarer.collections;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns collections.yml into definitions. A bad milestone is dropped and a bad collection skipped, each with a
 * problem; a reward already paid by another milestone is dropped (rewards are earned once per player).
 */
public final class CollectionConfigParser {
    static final Set<String> COMMON = Set.of("title", "colour", "blurb", "unit", "milestones", "hints");

    public record Settings(boolean bookOnFirstJoin, int entriesPerPage) {
    }

    public record Result(Map<String, CollectionDefinition> collections, Settings settings, List<String> problems) {
    }

    private CollectionConfigParser() {
    }

    public static Result parse(Map<String, Object> root) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> journal = asMap(root.get("journal"));
        int perPage = journal.get("entries-per-page") instanceof Number n ? n.intValue() : 16;
        if (perPage < 4 || perPage > 48) {
            problems.add("journal.entries-per-page must be 4 to 48; using 16");
            perPage = 16;
        }
        Settings settings = new Settings(!Boolean.FALSE.equals(journal.get("book-on-first-join")), perPage);
        Map<String, CollectionDefinition> out = new LinkedHashMap<>();
        Map<String, String> paidBy = new HashMap<>();
        asMap(root.get("collections")).forEach((id, value) -> {
            Map<String, Object> m = asMap(value);
            if (m.isEmpty()) {
                problems.add("Collection " + id + " skipped: not a section");
                return;
            }
            String colourText = String.valueOf(m.getOrDefault("colour", "#E8D9A8"));
            if (!colourText.matches("#[0-9a-fA-F]{6}")) {
                problems.add("Collection " + id + " skipped: colour must be #RRGGBB");
                return;
            }
            List<CollectionDefinition.Milestone> milestones = new ArrayList<>();
            asMap(m.get("milestones")).forEach((at, rewards) -> {
                String spec = at.trim();
                if (!spec.matches("\\d+") && !spec.matches("\\d{1,3}(\\.\\d+)?%")) {
                    problems.add("Collection " + id + ": milestone '" + at + "' is not a count or a share like 25%; dropped");
                    return;
                }
                if (spec.endsWith("%") && Double.parseDouble(spec.substring(0, spec.length() - 1)) > 100
                        || spec.equals("0") || spec.equals("0%")) {
                    problems.add("Collection " + id + ": milestone '" + at + "' must be above 0 and at most 100%; dropped");
                    return;
                }
                List<String> list = new ArrayList<>();
                for (Object o : rewards instanceof List<?> l ? l : List.of(rewards)) {
                    String reward = String.valueOf(o).trim();
                    int colon = reward.indexOf(':');
                    if (colon <= 0 || colon == reward.length() - 1) {
                        problems.add("Collection " + id + ": reward '" + o + "' is not type:id; dropped");
                    } else if (paidBy.containsKey(reward)) {
                        problems.add("Collection " + id + ": reward " + reward + " is already paid by " + paidBy.get(reward)
                                + " (each reward is earned once); dropped");
                    } else {
                        paidBy.put(reward, id);
                        list.add(reward.substring(0, colon).toLowerCase(Locale.ROOT) + reward.substring(colon));
                    }
                }
                if (!list.isEmpty()) {
                    milestones.add(new CollectionDefinition.Milestone(spec, list));
                }
            });
            Map<String, String> hints = new LinkedHashMap<>();
            asMap(m.get("hints")).forEach((k, v) -> hints.put(k.toLowerCase(Locale.ROOT), String.valueOf(v)));
            Map<String, Object> extra = new LinkedHashMap<>(m);
            extra.keySet().removeAll(COMMON);
            out.put(id, new CollectionDefinition(id, String.valueOf(m.getOrDefault("title", CollectionRules.title(id))),
                    Integer.parseInt(colourText.substring(1), 16), String.valueOf(m.getOrDefault("blurb", "")),
                    String.valueOf(m.getOrDefault("unit", "entry|entries")), milestones, hints, extra));
        });
        return new Result(out, settings, List.copyOf(problems));
    }

    static Map<String, Object> asMap(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }

    /** A setting that is a list of strings, lower-case. */
    static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        for (Object o : value instanceof List<?> l ? l : value == null ? List.of() : List.of(value)) {
            out.add(String.valueOf(o).toLowerCase(Locale.ROOT));
        }
        return out;
    }
}
