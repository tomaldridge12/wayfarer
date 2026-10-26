package dev.wayfarer.comforts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** comforts.yml: the leather recipe's ratio and the death rule's switches. Bad values give a problem line and a default. */
public final class ComfortsConfigParser {
    /** The recipe takes at most a full crafting grid. */
    static final int MAX_FLESH = 9;
    static final int MAX_LEATHER = 64;

    public record Settings(boolean leatherFromFlesh, int flesh, int leather, boolean keepOnDeath, boolean hotbar, boolean armour,
            boolean offhand) {
    }

    public record Result(Settings settings, List<String> problems) {
    }

    private ComfortsConfigParser() {
    }

    public static Result parse(Map<String, Object> root) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> leather = asMap(root.get("leather-from-flesh"));
        Map<String, Object> keep = asMap(root.get("keep-on-death"));
        Settings settings = new Settings(
                flag(leather, "leather-from-flesh", "enabled", problems),
                whole(leather, "leather-from-flesh", "flesh", 4, MAX_FLESH, problems),
                whole(leather, "leather-from-flesh", "leather", 1, MAX_LEATHER, problems),
                flag(keep, "keep-on-death", "enabled", problems),
                flag(keep, "keep-on-death", "hotbar", problems),
                flag(keep, "keep-on-death", "armour", problems),
                flag(keep, "keep-on-death", "offhand", problems));
        return new Result(settings, List.copyOf(problems));
    }

    /** A missing switch is true; a value that is not true or false is a problem and true. */
    private static boolean flag(Map<String, Object> values, String section, String name, List<String> problems) {
        Object value = values.get(name);
        if (value instanceof Boolean b) {
            return b;
        }
        if (value != null) {
            problems.add(section + "." + name + " must be true or false; using true");
        }
        return true;
    }

    private static int whole(Map<String, Object> values, String section, String name, int fallback, int max, List<String> problems) {
        Object value = values.get(name);
        if (value instanceof Number n && n.intValue() >= 1 && n.intValue() <= max && n.doubleValue() == n.intValue()) {
            return n.intValue();
        }
        if (value != null) {
            problems.add(section + "." + name + " must be a whole number from 1 to " + max + "; using " + fallback);
        }
        return fallback;
    }

    private static Map<String, Object> asMap(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }
}
