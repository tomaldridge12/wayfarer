package dev.wayfarer.foods;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** foods.yml: the forage ingredients. A bad entry gives a problem line and is skipped or falls back to a default. */
public final class FoodsConfigParser {
    static final double DEFAULT_CHANCE = 0.04;
    static final int DEFAULT_PITY = 40;

    public record Result(List<ForageDefinition> forage, List<String> problems) {
    }

    private FoodsConfigParser() {
    }

    /**
     * @param isPlant whether a block key (no namespace) names a block this server has
     * @param isBiome whether a biome key or {@code #tag} exists on this server
     */
    public static Result parse(Map<String, Object> root, Predicate<String> isPlant, Predicate<String> isBiome) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> forage = map(root.get("forage"));
        double defaultChance = chance(forage.get("chance"), DEFAULT_CHANCE, "forage.chance", problems);
        int defaultPity = pity(forage.get("pity"), DEFAULT_PITY, "forage.pity", problems);
        List<ForageDefinition> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : map(forage.get("ingredients")).entrySet()) {
            String id = e.getKey().toLowerCase(Locale.ROOT);
            String where = "forage." + id;
            Map<String, Object> m = map(e.getValue());
            String name = m.get("name") == null ? null : String.valueOf(m.get("name"));
            if (name == null || name.isBlank()) {
                problems.add(where + " has no name; skipped");
                continue;
            }
            List<String> biomes = new ArrayList<>();
            for (String b : strings(m.get("biomes"))) {
                String key = b.toLowerCase(Locale.ROOT).replaceFirst("^(#?)minecraft:", "$1");
                if (isBiome.test(key)) {
                    biomes.add(key);
                } else {
                    problems.add(where + ".biomes '" + b + "' is not a biome or biome tag here; skipped");
                }
            }
            Set<String> plants = new LinkedHashSet<>();
            for (String p : strings(m.get("plants"))) {
                String key = p.toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "");
                if (isPlant.test(key)) {
                    plants.add(key);
                } else {
                    problems.add(where + ".plants '" + p + "' is not a block; skipped");
                }
            }
            if (biomes.isEmpty() || plants.isEmpty()) {
                problems.add(where + " has no usable " + (biomes.isEmpty() ? "biomes" : "plants") + "; skipped");
                continue;
            }
            out.add(new ForageDefinition(id, name, colour(m.get("colour"), where, problems), strings(m.get("description")),
                    m.get("flavour") == null ? "" : String.valueOf(m.get("flavour")), List.copyOf(biomes), Set.copyOf(plants),
                    chance(m.get("chance"), defaultChance, where + ".chance", problems),
                    pity(m.get("pity"), defaultPity, where + ".pity", problems)));
        }
        return new Result(List.copyOf(out), List.copyOf(problems));
    }

    private static double chance(Object value, double fallback, String path, List<String> problems) {
        if (value instanceof Number n && n.doubleValue() > 0 && n.doubleValue() <= 1) {
            return n.doubleValue();
        }
        if (value != null) {
            problems.add(path + " must be above 0 and at most 1; using " + fallback);
        }
        return fallback;
    }

    private static int pity(Object value, int fallback, String path, List<String> problems) {
        if (value instanceof Number n && n.intValue() >= 1 && n.doubleValue() == n.intValue()) {
            return n.intValue();
        }
        if (value != null) {
            problems.add(path + " must be a whole number of 1 or more; using " + fallback);
        }
        return fallback;
    }

    private static int colour(Object value, String where, List<String> problems) {
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).replace("#", ""), 16) & 0xFFFFFF;
            } catch (NumberFormatException e) {
                problems.add(where + ".colour '" + value + "' is not #RRGGBB; using the default");
            }
        }
        return 0xE8D9A8;
    }

    private static List<String> strings(Object value) {
        List<String> out = new ArrayList<>();
        if (value instanceof List<?> list) {
            list.forEach(v -> out.add(String.valueOf(v)));
        } else if (value != null) {
            out.add(String.valueOf(value));
        }
        return out;
    }

    private static Map<String, Object> map(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> m) {
            m.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }
}
