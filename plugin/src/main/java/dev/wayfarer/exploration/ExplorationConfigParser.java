package dev.wayfarer.exploration;

import dev.wayfarer.core.XpPoints;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * exploration.yml: Wayfinder prices (in XP points), range, cooldown, the structures it offers, and beacon kindling. A
 * file written in levels (version 1: {@code structure-levels}, {@code biome-levels}, {@code chart-levels} and a {@code rare}
 * map of levels) is read by converting each level number to the points it takes to reach that level from 0 (ADR 0007).
 */
public final class ExplorationConfigParser {
    /** Settings, with the structures the Wayfinder offers (structure key to its display name, in order). */
    public record Settings(ExplorationRules.Prices prices, int rangeBlocks, int cooldownSeconds, int chartPoints,
            Map<String, String> structures, int kindlingSeconds, int traderDelaySeconds) {
    }

    /** {@code notes} are info lines for the log (a converted file), {@code problems} are warnings. */
    public record Result(Settings settings, List<String> problems, List<String> notes) {
    }

    private ExplorationConfigParser() {
    }

    public static Result parse(Map<String, Object> root) {
        List<String> problems = new ArrayList<>();
        List<String> converted = new ArrayList<>();
        Map<String, Object> w = asMap(root.get("wayfinder"));
        boolean levelsFile = !(root.get("version") instanceof Number v && v.intValue() >= 2);
        Map<String, Integer> rare = new LinkedHashMap<>();
        asMap(w.get("rare")).forEach((k, v) -> {
            if (v instanceof Number n && n.intValue() >= 0) {
                rare.put(k, levelsFile ? XpPoints.toReach(n.intValue()) : n.intValue());
            } else {
                problems.add("wayfinder.rare." + k + " must be a number of " + (levelsFile ? "levels" : "XP points") + "; ignored");
            }
        });
        if (levelsFile && !rare.isEmpty()) {
            converted.add("rare");
        }
        Map<String, String> structures = new LinkedHashMap<>();
        asMap(w.get("structures")).forEach((k, v) -> structures.put(k.contains(":") ? k : "minecraft:" + k, String.valueOf(v)));
        Map<String, Object> k = asMap(root.get("beacon-kindling"));
        double multiplier = 2;
        if (w.get("unfound-multiplier") instanceof Number n && n.doubleValue() >= 1) {
            multiplier = n.doubleValue();
        } else if (w.get("unfound-multiplier") != null) {
            problems.add("wayfinder.unfound-multiplier must be a number of 1 or more; using 2");
        }
        Settings settings = new Settings(new ExplorationRules.Prices(points(w, "structure", 55, problems, converted),
                points(w, "biome", 27, problems, converted), rare, multiplier),
                number(w.get("range"), 3200, problems, "wayfinder.range"), number(w.get("cooldown"), 300, problems, "wayfinder.cooldown"),
                points(w, "chart", 112, problems, converted), structures,
                number(k.get("seconds"), 90, problems, "beacon-kindling.seconds"),
                number(k.get("trader-after"), 12, problems, "beacon-kindling.trader-after"));
        List<String> notes = converted.isEmpty() ? List.of() : List.of("exploration.yml prices are in levels (version 1); read as XP points ("
                + String.join(", ", converted) + "). Wayfarer does not rewrite your file; to price in points use structure-points, biome-points,"
                + " chart-points and rare in points, with version: 2");
        return new Result(settings, List.copyOf(problems), notes);
    }

    /** {@code <name>-points} if set, else the old {@code <name>-levels} converted to points (noted), else the default. */
    private static int points(Map<String, Object> w, String name, int fallback, List<String> problems, List<String> converted) {
        if (w.get(name + "-points") != null) {
            return number(w.get(name + "-points"), fallback, problems, "wayfinder." + name + "-points");
        }
        if (w.get(name + "-levels") instanceof Number n && n.intValue() >= 0) {
            converted.add(name + "-levels " + n.intValue() + " = " + XpPoints.toReach(n.intValue()) + " XP");
            return XpPoints.toReach(n.intValue());
        } else if (w.get(name + "-levels") != null) {
            problems.add("wayfinder." + name + "-levels must be a whole number of levels; using " + fallback + " XP");
        }
        return fallback;
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
