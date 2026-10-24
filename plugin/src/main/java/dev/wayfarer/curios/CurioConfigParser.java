package dev.wayfarer.curios;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Turns the curios section of curios.yml into definitions. A bad curio is skipped with a problem; the rest load. */
public final class CurioConfigParser {
    static final Set<String> XP_SOURCES = Set.of("fishing", "ores", "mobs");
    static final Set<String> OPERATIONS = Set.of("add", "multiply_base", "multiply_total");

    private final CurioVocabulary names;
    private final List<String> problems = new ArrayList<>();

    private CurioConfigParser(CurioVocabulary names) {
        this.names = names;
    }

    public record Result(Map<String, CurioDefinition> curios, List<String> problems) {
    }

    public static Result parse(Object section, CurioVocabulary names) {
        CurioConfigParser p = new CurioConfigParser(names);
        Map<String, CurioDefinition> out = new LinkedHashMap<>();
        asMap(section).forEach((id, value) -> {
            try {
                if (!id.matches("[a-z0-9_]+")) {
                    throw new Bad("ids are lower-case letters, digits and _");
                }
                out.put(id, p.curio(id, asMap(value)));
            } catch (Bad e) {
                p.problems.add("Curio " + id + " skipped: " + e.getMessage());
            }
        });
        return new Result(out, List.copyOf(p.problems));
    }

    /** A reason a curio cannot load. */
    static final class Bad extends Exception {
        Bad(String message) {
            super(message);
        }
    }

    private CurioDefinition curio(String id, Map<String, Object> m) throws Bad {
        if (m.isEmpty()) {
            throw new Bad("not a section");
        }
        String name = text(m.get("name"), title(id));
        Effect effect = effect(String.valueOf(m.get("effect")).toLowerCase(Locale.ROOT), m);
        List<Condition> conditions = new ArrayList<>();
        Object when = m.get("when");
        for (Object o : when instanceof List<?> l ? l : when == null ? List.of() : List.of(when)) {
            conditions.add(Condition.parse(String.valueOf(o)).orElseThrow(() -> new Bad("when '" + o
                    + "' is not a condition (always, underground, underwater, nether, end, night, rain, near_water,"
                    + " wearing_alloy, below <y>, biome #<tag>, holding <tool>)")));
        }
        int cooldown = m.get("cooldown") instanceof Number n ? n.intValue() : 0;
        if (effect.tool() && cooldown <= 0) {
            throw new Bad("a tool curio needs a cooldown in seconds");
        }
        if (effect.tool() && !conditions.isEmpty()) {
            throw new Bad("tool curios work anywhere; 'when' is for charms");
        }
        List<String> description = new ArrayList<>();
        Object d = m.get("description");
        for (Object o : d instanceof List<?> l ? l : d == null ? List.of() : List.of(d)) {
            description.add(String.valueOf(o));
        }
        if (description.isEmpty()) {
            throw new Bad("description is missing (one or two lines saying what it does)");
        }
        return new CurioDefinition(id, name, colour(m.get("colour")), description, text(m.get("flavour"), ""), effect,
                conditions, cooldown);
    }

    private Effect effect(String type, Map<String, Object> m) throws Bad {
        return switch (type) {
            case "potion" -> {
                Set<String> potions = keys(m.containsKey("potions") ? m.get("potions") : m.get("potion"), names::isPotion, "potion");
                yield new Effect.Potion(List.copyOf(potions), integer(m, "amplifier", 0, 0, 4));
            }
            case "attribute" -> {
                String attribute = key(m.get("attribute"), names::isAttribute, "attribute");
                String op = String.valueOf(m.getOrDefault("operation", "add")).toLowerCase(Locale.ROOT);
                if (!OPERATIONS.contains(op)) {
                    throw new Bad("operation must be add, multiply_base or multiply_total");
                }
                if (!(m.get("amount") instanceof Number n)) {
                    throw new Bad("attribute needs an amount");
                }
                yield new Effect.Attribute(attribute, n.doubleValue(), op);
            }
            case "immunity" -> {
                Set<String> effects = optionalKeys(m.get("effects"), names::isPotion, "potion");
                Set<String> causes = optionalKeys(m.get("causes"), names::isPotionCause, "potion cause");
                Set<String> damage = optionalKeys(m.get("damage"), names::isDamageCause, "damage cause");
                Set<String> splash = optionalKeys(m.get("splash-from"), names::isMob, "mob");
                if (effects.isEmpty() && damage.isEmpty() && splash.isEmpty()) {
                    throw new Bad("immunity needs effects, damage or splash-from");
                }
                yield new Effect.Immunity(effects, causes, damage, splash);
            }
            case "ignored" -> new Effect.Ignored(keys(m.get("mobs"), names::isMob, "mob"),
                    Boolean.TRUE.equals(m.get("even-when-hit")));
            case "drop_bonus" -> new Effect.DropBonus(blocks(m.get("blocks")), key(m.get("item"), names::isItem, "item"),
                    integer(m, "amount", 1, 1, 64), chance(m, "chance", 1));
            case "grow" -> new Effect.Grow(integer(m, "radius", 5, 1, 12), integer(m, "every", 4, 1, 600));
            case "xp_bonus" -> {
                Set<String> sources = optionalKeys(m.get("sources"), XP_SOURCES::contains, "xp source (fishing, ores, mobs)");
                if (sources.isEmpty() || !(m.get("multiplier") instanceof Number n) || n.doubleValue() < 1) {
                    throw new Bad("xp_bonus needs sources and a multiplier of 1 or more");
                }
                yield new Effect.XpBonus(sources, n.doubleValue());
            }
            case "fall_guard" -> new Effect.FallGuard(number(m, "distance", 6, 2, 64));
            case "magnet" -> new Effect.Magnet(number(m, "radius", 6, 1, 16));
            case "boost" -> {
                if (!(m.get("what") instanceof String what) || !(m.get("factor") instanceof Number f) || f.doubleValue() <= 0) {
                    throw new Bad("boost needs what (such as treasure) and a factor above 0");
                }
                yield new Effect.Boost(what.toLowerCase(Locale.ROOT), f.doubleValue());
            }
            case "chorus" -> new Effect.Chorus();
            case "spread" -> {
                Map<String, String> turns = new LinkedHashMap<>();
                for (var e : asMap(m.get("turns")).entrySet()) {
                    String from = e.getKey().toLowerCase(Locale.ROOT);
                    String to = String.valueOf(e.getValue()).toLowerCase(Locale.ROOT);
                    if (!names.isBlock(from) || !names.isBlock(to)) {
                        throw new Bad("turns " + from + " -> " + to + " is not block -> block");
                    }
                    turns.put(from, to);
                }
                if (turns.isEmpty()) {
                    throw new Bad("spread needs turns (from: to)");
                }
                yield new Effect.Spread(integer(m, "radius", 4, 1, 8), chance(m, "chance", 0.5), turns);
            }
            case "light" -> new Effect.Light(integer(m, "seconds", 60, 1, 600));
            case "sense" -> new Effect.Sense(blocks(m.get("blocks")), integer(m, "radius", 12, 1, 24),
                    integer(m, "seconds", 10, 1, 60));
            case "recall" -> new Effect.Recall(integer(m, "channel", 5, 0, 30));
            case "locate" -> new Effect.Locate();
            default -> throw new Bad("effect '" + type + "' is not one of potion, attribute, immunity, ignored,"
                    + " drop_bonus, grow, xp_bonus, fall_guard, magnet, boost, chorus, spread, light, sense, recall, locate");
        };
    }

    private Set<String> blocks(Object value) throws Bad {
        Set<String> out = new LinkedHashSet<>();
        for (Object o : value instanceof List<?> l ? l : value == null ? List.of() : List.of(value)) {
            String k = String.valueOf(o).toLowerCase(Locale.ROOT);
            if (k.startsWith("#")) {
                Set<String> tagged = names.tag(k.substring(1));
                if (tagged.isEmpty()) {
                    throw new Bad("tag " + k + " is empty or unknown");
                }
                out.addAll(tagged);
            } else if (names.isBlock(k)) {
                out.add(k);
            } else {
                throw new Bad("'" + o + "' is not a block");
            }
        }
        if (out.isEmpty()) {
            throw new Bad("blocks lists nothing");
        }
        return Set.copyOf(out);
    }

    private Set<String> keys(Object value, Predicate<String> known, String what) throws Bad {
        Set<String> out = optionalKeys(value, known, what);
        if (out.isEmpty()) {
            throw new Bad("no " + what + " given");
        }
        return out;
    }

    private Set<String> optionalKeys(Object value, Predicate<String> known, String what) throws Bad {
        Set<String> out = new LinkedHashSet<>();
        for (Object o : value instanceof List<?> l ? l : value == null ? List.of() : List.of(value)) {
            out.add(key(o, known, what));
        }
        return out;
    }

    private String key(Object value, Predicate<String> known, String what) throws Bad {
        String k = String.valueOf(value).toLowerCase(Locale.ROOT);
        if (value == null || !known.test(k)) {
            throw new Bad("'" + value + "' is not a " + what);
        }
        return k;
    }

    private static int integer(Map<String, Object> m, String key, int fallback, int min, int max) throws Bad {
        Object v = m.get(key);
        int n = v == null ? fallback : v instanceof Number num ? num.intValue() : Integer.MIN_VALUE;
        if (n < min || n > max) {
            throw new Bad(key + " must be a whole number from " + min + " to " + max);
        }
        return n;
    }

    private static double number(Map<String, Object> m, String key, double fallback, double min, double max) throws Bad {
        Object v = m.get(key);
        double n = v == null ? fallback : v instanceof Number num ? num.doubleValue() : Double.NaN;
        if (!(n >= min && n <= max)) {
            throw new Bad(key + " must be a number from " + min + " to " + max);
        }
        return n;
    }

    private static double chance(Map<String, Object> m, String key, double fallback) throws Bad {
        return number(m, key, fallback, 0, 1);
    }

    private int colour(Object value) throws Bad {
        if (value == null) {
            return 0xE8D9A8;
        }
        String s = String.valueOf(value);
        if (!s.matches("#[0-9a-fA-F]{6}")) {
            throw new Bad("colour must be #RRGGBB");
        }
        return Integer.parseInt(s.substring(1), 16);
    }

    private static String text(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String w : id.split("_")) {
            if (!w.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1));
            }
        }
        return out.toString();
    }

    static Map<String, Object> asMap(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }
}
