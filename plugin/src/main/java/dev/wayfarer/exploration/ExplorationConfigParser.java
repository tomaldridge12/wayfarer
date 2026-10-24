package dev.wayfarer.exploration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** exploration.yml: Wayfinder prices, range, cooldown, the structures it offers, and beacon kindling. */
public final class ExplorationConfigParser {
    /** Settings, with the structures the Wayfinder offers (structure key to its display name, in order). */
    public record Settings(ExplorationRules.Prices prices, int rangeBlocks, int cooldownSeconds, int chartLevels,
            Map<String, String> structures, int kindlingSeconds, int traderDelaySeconds) {
    }

    public record Result(Settings settings, List<String> problems) {
    }

    private ExplorationConfigParser() {
    }

    public static Result parse(Map<String, Object> root) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> w = asMap(root.get("wayfinder"));
        Map<String, Integer> rare = new LinkedHashMap<>();
        asMap(w.get("rare")).forEach((k, v) -> {
            if (v instanceof Number n && n.intValue() >= 0) {
                rare.put(k, n.intValue());
            } else {
                problems.add("wayfinder.rare." + k + " must be a number of levels; ignored");
            }
        });
        Map<String, String> structures = new LinkedHashMap<>();
        asMap(w.get("structures")).forEach((k, v) -> structures.put(k.contains(":") ? k : "minecraft:" + k, String.valueOf(v)));
        Map<String, Object> k = asMap(root.get("beacon-kindling"));
        return new Result(new Settings(new ExplorationRules.Prices(number(w.get("structure-levels"), 5, problems, "wayfinder.structure-levels"),
                number(w.get("biome-levels"), 3, problems, "wayfinder.biome-levels"), rare),
                number(w.get("range"), 3200, problems, "wayfinder.range"), number(w.get("cooldown"), 300, problems, "wayfinder.cooldown"),
                number(w.get("chart-levels"), 8, problems, "wayfinder.chart-levels"), structures,
                number(k.get("seconds"), 90, problems, "beacon-kindling.seconds"),
                number(k.get("trader-after"), 12, problems, "beacon-kindling.trader-after")), List.copyOf(problems));
    }

    private static int number(Object o, int fallback, List<String> problems, String where) {
        if (o == null) {
            return fallback;
        }
        if (o instanceof Number n && n.intValue() >= 0) {
            return n.intValue();
        }
        problems.add(where + " must be a whole number of 0 or more; using " + fallback);
        return fallback;
    }

    static Map<String, Object> asMap(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, v) -> out.put(String.valueOf(key), v));
        }
        return out;
    }
}
