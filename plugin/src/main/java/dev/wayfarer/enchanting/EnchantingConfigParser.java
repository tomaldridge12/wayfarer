package dev.wayfarer.enchanting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/** enchanting.yml: the reshuffle's on/off switch, its price in levels and the item that triggers it. */
public final class EnchantingConfigParser {
    /** The reshuffle settings; {@code trigger} is an item key without a namespace. */
    public record Settings(boolean reshuffle, int levels, String trigger) {
    }

    public record Result(Settings settings, List<String> problems) {
    }

    private EnchantingConfigParser() {
    }

    /** Reads the config; {@code isItem} says whether a key names an item this server has. Bad values give a problem and a default. */
    public static Result parse(Map<String, Object> root, Predicate<String> isItem) {
        List<String> problems = new ArrayList<>();
        Map<String, Object> r = asMap(root.get("reshuffle"));
        boolean on = true;
        if (r.get("enabled") instanceof Boolean b) {
            on = b;
        } else if (r.get("enabled") != null) {
            problems.add("reshuffle.enabled must be true or false; using true");
        }
        int levels = 1;
        if (r.get("levels") instanceof Number n && n.intValue() >= 1) {
            levels = n.intValue();
        } else if (r.get("levels") != null) {
            problems.add("reshuffle.levels must be a whole number of 1 or more; using 1");
        }
        String trigger = "lapis_lazuli";
        if (r.get("trigger") != null) {
            String wanted = String.valueOf(r.get("trigger")).toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "");
            if (isItem.test(wanted)) {
                trigger = wanted;
            } else {
                problems.add("reshuffle.trigger '" + r.get("trigger") + "' is not an item; using lapis_lazuli");
            }
        }
        return new Result(new Settings(on, levels, trigger), List.copyOf(problems));
    }

    private static Map<String, Object> asMap(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }
}
