package dev.wayfarer.marks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Turns marks.yml into {@link MarkRules}. A bad mark or book is skipped with a problem; the rest still load. */
public final class MarkConfigParser {
    private final MarkVocabulary vocabulary;
    private final List<String> problems = new ArrayList<>();
    private final List<String> notices = new ArrayList<>();

    private MarkConfigParser(MarkVocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    /** Rules, what was wrong, and what is merely worth knowing (a book whose enchant this server lacks). */
    public record Result(MarkRules rules, List<String> problems, List<String> notices) {
    }

    public static Result parse(Map<String, Object> root, MarkVocabulary vocabulary) {
        return new MarkConfigParser(vocabulary).run(root);
    }

    private Result run(Map<String, Object> root) {
        Map<String, MarkDefinition> marks = new LinkedHashMap<>();
        asMap(root.get("marks")).forEach((id, value) -> {
            MarkDefinition m = parseMark(id, value);
            if (m != null) {
                marks.put(id, m);
            }
        });
        Map<String, BookDefinition> books = new LinkedHashMap<>();
        asMap(root.get("books")).forEach((id, value) -> {
            BookDefinition b = parseBook(id, value);
            if (b == null) {
                return;
            }
            if (marks.containsKey(id)) {
                problems.add("Book " + id + " skipped: a mark already uses that ladder id");
                return;
            }
            books.put(id, b);
        });
        return new Result(new MarkRules(marks, books), List.copyOf(problems), List.copyOf(notices));
    }

    private MarkDefinition parseMark(String id, Object value) {
        String where = "Mark " + id + " skipped: ";
        if (!MarkDefinition.KNOWN.contains(id)) {
            problems.add(where + "no mark of that name (marks: " + String.join(", ", MarkDefinition.KNOWN) + ")");
            return null;
        }
        if (!(value instanceof Map<?, ?>)) {
            problems.add(where + "not a section");
            return null;
        }
        Map<String, Object> m = asMap(value);
        String name = String.valueOf(m.getOrDefault("name", title(id)));
        Set<String> selector = selector(m.get("counts"), where);
        List<Long> thresholds = thresholds(m.get("ranks-at"), where);
        var tool = ToolType.parse(m.get("tool"));
        if (tool.isEmpty()) {
            problems.add(where + "tool must be shovel, shears, fishing_rod or hoe");
            return null;
        }
        Set<String> materials = items(m.get("material"), where);
        if (selector == null || thresholds == null || materials == null) {
            return null;
        }
        if (!(m.get("ranks") instanceof List<?> list) || list.size() != thresholds.size()) {
            problems.add(where + "ranks must list one entry per threshold in ranks-at");
            return null;
        }
        List<MarkDefinition.Rank> ranks = new ArrayList<>();
        for (Object o : list) {
            MarkDefinition.Rank r = parseRank(id, asMap(o), where);
            if (r == null) {
                return null;
            }
            ranks.add(r);
        }
        return new MarkDefinition(id, name, colour(m.get("colour"), id), String.valueOf(m.getOrDefault("unit", "block")),
                selector, thresholds, tool.get(), materials, List.copyOf(ranks));
    }

    private MarkDefinition.Rank parseRank(String id, Map<String, Object> r, String where) {
        int width = 1;
        int depth = 1;
        if (id.equals("dune") || id.equals("reaper")) {
            String area = String.valueOf(r.get("area"));
            if (!area.matches("[1-9]x[1-9]") || area.charAt(0) % 2 == 0 || area.charAt(2) % 2 == 0) {
                problems.add(where + "area must be odd x odd, such as 3x1 or 3x3 (was " + r.get("area") + ")");
                return null;
            }
            width = area.charAt(0) - '0';
            depth = area.charAt(2) - '0';
        }
        int radius = r.get("radius") instanceof Number n ? n.intValue() : 0;
        if (id.equals("shepherd") && (radius < 1 || radius > 16)) {
            problems.add(where + "radius must be 1 to 16");
            return null;
        }
        double wait = r.get("bite-wait") instanceof Number n ? n.doubleValue() : 1.0;
        if (id.equals("angler") && (wait <= 0 || wait > 1)) {
            problems.add(where + "bite-wait must be above 0 and at most 1 (0.85 is 15% sooner)");
            return null;
        }
        Object rawChance = r.get(id.equals("angler") ? "treasure" : "extra-wool");
        double chance = rawChance instanceof Number n ? n.doubleValue() : 0;
        if (chance < 0 || chance > 1) {
            problems.add(where + "chances are from 0 to 1");
            return null;
        }
        return new MarkDefinition.Rank(width, depth, radius, wait, chance, Boolean.TRUE.equals(r.get("gravel")));
    }

    private BookDefinition parseBook(String id, Object value) {
        String where = "Book " + id + " skipped: ";
        if (!(value instanceof Map<?, ?>)) {
            problems.add(where + "not a section");
            return null;
        }
        Map<String, Object> m = asMap(value);
        String enchant = String.valueOf(m.get("enchant")).toLowerCase(Locale.ROOT);
        if (!enchant.contains(":")) {
            problems.add(where + "enchant must be a namespaced key such as excellentenchants:veinminer");
            return null;
        }
        Set<String> selector = selector(m.get("counts"), where);
        List<Long> thresholds = thresholds(m.get("levels-at"), where);
        if (selector == null || thresholds == null) {
            return null;
        }
        if (!vocabulary.isEnchant(enchant)) {
            notices.add("Book " + id + ": " + enchant + " is not on this server; its ladder still counts, but its books"
                    + " cannot be given until the enchant is back");
        }
        return new BookDefinition(id, String.valueOf(m.getOrDefault("name", title(id))), enchant,
                String.valueOf(m.getOrDefault("unit", "block")), selector, thresholds);
    }

    /** Activity keys; {@code mine:#minecraft:logs} becomes one entry per block in the tag; {@code catch:*} stays a prefix. */
    private Set<String> selector(Object value, String where) {
        Set<String> out = new LinkedHashSet<>();
        if (value instanceof List<?> list) {
            for (Object o : list) {
                String entry = String.valueOf(o).toLowerCase(Locale.ROOT);
                int cut = entry.indexOf(':');
                if (cut <= 0 || cut == entry.length() - 1) {
                    problems.add(where.replace(" skipped: ", ": ") + "counts entry '" + o + "' is not verb:target; left out");
                    continue;
                }
                String verb = entry.substring(0, cut);
                String target = entry.substring(cut + 1);
                if (target.startsWith("#")) {
                    Set<String> keys = vocabulary.tag(target.substring(1));
                    if (keys.isEmpty()) {
                        problems.add(where.replace(" skipped: ", ": ") + "tag " + target + " is empty or unknown; left out");
                    }
                    keys.forEach(k -> out.add(verb + ":" + k));
                } else {
                    out.add(entry);
                }
            }
        }
        if (out.isEmpty()) {
            problems.add(where + "counts lists no activities");
            return null;
        }
        return Set.copyOf(out);
    }

    private Set<String> items(Object value, String where) {
        Set<String> out = new LinkedHashSet<>();
        for (Object o : value instanceof List<?> list ? list : value == null ? List.of() : List.of(value)) {
            String key = String.valueOf(o).toLowerCase(Locale.ROOT);
            if (key.startsWith("#")) {
                out.addAll(vocabulary.tag(key.substring(1)));
            } else if (vocabulary.isItem(key)) {
                out.add(key);
            } else {
                problems.add(where.replace(" skipped: ", ": ") + "material '" + o + "' is not an item; left out");
            }
        }
        if (out.isEmpty()) {
            problems.add(where + "material lists no items");
            return null;
        }
        return Set.copyOf(out);
    }

    private List<Long> thresholds(Object value, String where) {
        if (value instanceof List<?> list && !list.isEmpty() && list.stream().allMatch(v -> v instanceof Number n && n.longValue() > 0)) {
            List<Long> out = list.stream().map(v -> ((Number) v).longValue()).toList();
            for (int i = 1; i < out.size(); i++) {
                if (out.get(i) <= out.get(i - 1)) {
                    problems.add(where + "thresholds must rise");
                    return null;
                }
            }
            return out;
        }
        problems.add(where + "thresholds must be a list of positive numbers");
        return null;
    }

    private int colour(Object value, String id) {
        if (value != null && String.valueOf(value).matches("#[0-9a-fA-F]{6}")) {
            return Integer.parseInt(String.valueOf(value).substring(1), 16);
        }
        if (value != null) {
            problems.add("Mark " + id + ": colour must be #RRGGBB; using white");
        }
        return 0xFFFFFF;
    }

    private static String title(String id) {
        return id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
    }

    private static Map<String, Object> asMap(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }
}
