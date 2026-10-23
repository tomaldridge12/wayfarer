package dev.wayfarer.banners;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns the banners config (nested maps and lists) into valid definitions.
 * Bad entries are rejected one by one with a message; the rest still load.
 */
public final class BannerConfigParser {
    static final int DEFAULT_KILLS = 100;
    static final int DEFAULT_DAMAGE_BONUS = 10;
    static final int DEFAULT_DAMAGE_REDUCTION = 10;
    static final int DEFAULT_RADIUS = 16;
    static final String DEFAULT_COLOUR = "white";

    private final Vocabulary vocabulary;
    private final List<String> problems = new ArrayList<>();

    private BannerConfigParser(Vocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    public static BannerSet parse(Map<String, Object> root, Vocabulary vocabulary) {
        return new BannerConfigParser(vocabulary).run(root);
    }

    private BannerSet run(Map<String, Object> root) {
        Map<String, MobFamily> families = parseFamilies(asMap(root.get("families")));
        Map<String, BannerDefinition> banners = new LinkedHashMap<>();
        Map<String, Object> raw = asMap(root.get("banners"));
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            BannerDefinition banner = parseBanner(entry.getKey(), entry.getValue(), families);
            if (banner != null) {
                banners.put(banner.id(), banner);
            }
        }
        return new BannerSet(families, banners, List.copyOf(problems));
    }

    /** A family with an unknown mob, or a mob already in an earlier family, is rejected whole. */
    private Map<String, MobFamily> parseFamilies(Map<String, Object> raw) {
        Map<String, MobFamily> families = new LinkedHashMap<>();
        Map<String, String> owner = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String id = entry.getKey();
            if (!(entry.getValue() instanceof List<?> list) || list.isEmpty()) {
                problems.add("Family " + id + " rejected: must be a non-empty list of mob types");
                continue;
            }
            Set<String> mobs = new LinkedHashSet<>();
            String error = null;
            for (Object item : list) {
                String mob = String.valueOf(item).toLowerCase(Locale.ROOT);
                if (!vocabulary.isMob(mob)) {
                    error = "Family " + id + " rejected: unknown mob type '" + item + "'";
                    break;
                }
                if (owner.containsKey(mob)) {
                    error = "Family " + id + " rejected: mob type " + mob + " is already in family " + owner.get(mob);
                    break;
                }
                mobs.add(mob);
            }
            if (error != null) {
                problems.add(error);
                continue;
            }
            mobs.forEach(mob -> owner.put(mob, id));
            families.put(id, new MobFamily(id, Set.copyOf(mobs)));
        }
        return families;
    }

    private BannerDefinition parseBanner(String id, Object value, Map<String, MobFamily> families) {
        String where = "Banner " + id + " skipped: ";
        if (!(value instanceof Map<?, ?>)) {
            problems.add(where + "not a section");
            return null;
        }
        Map<String, Object> m = asMap(value);
        Object familyValue = m.get("family");
        if (familyValue == null) {
            problems.add(where + "missing family");
            return null;
        }
        String family = String.valueOf(familyValue);
        if (!families.containsKey(family)) {
            problems.add(where + "family '" + family + "' is not defined or was rejected");
            return null;
        }
        String colour = String.valueOf(m.getOrDefault("colour", DEFAULT_COLOUR)).toLowerCase(Locale.ROOT);
        if (!vocabulary.isColour(colour)) {
            problems.add(where + "unknown colour '" + colour + "'");
            return null;
        }
        List<BannerDefinition.PatternLayer> patterns = parsePatterns(m.get("patterns"), where);
        Integer kills = number(m, "kills", DEFAULT_KILLS, 1, Integer.MAX_VALUE, where);
        Integer bonus = number(m, "damage-bonus", DEFAULT_DAMAGE_BONUS, 0, 1000, where);
        Integer reduction = number(m, "damage-reduction", DEFAULT_DAMAGE_REDUCTION, 0, 100, where);
        Integer radius = number(m, "radius", DEFAULT_RADIUS, 1, 128, where);
        if (patterns == null || kills == null || bonus == null || reduction == null || radius == null) {
            return null;
        }
        String name = String.valueOf(m.getOrDefault("name", id));
        return new BannerDefinition(id, name, colour, patterns, family, kills, bonus, reduction, radius);
    }

    /** Each layer is written `pattern:colour`, for example `creeper:lime`. */
    private List<BannerDefinition.PatternLayer> parsePatterns(Object value, String where) {
        List<BannerDefinition.PatternLayer> layers = new ArrayList<>();
        if (value == null) {
            return layers;
        }
        if (!(value instanceof List<?> list)) {
            problems.add(where + "patterns must be a list of pattern:colour");
            return null;
        }
        for (Object item : list) {
            String[] parts = String.valueOf(item).toLowerCase(Locale.ROOT).split(":");
            if (parts.length != 2 || !vocabulary.isPattern(parts[0])) {
                problems.add(where + "bad pattern '" + item + "'");
                return null;
            }
            if (!vocabulary.isColour(parts[1])) {
                problems.add(where + "bad pattern colour in '" + item + "'");
                return null;
            }
            layers.add(new BannerDefinition.PatternLayer(parts[0], parts[1]));
        }
        return layers;
    }

    private Integer number(Map<String, Object> m, String key, int fallback, int min, int max, String where) {
        Object value = m.get(key);
        if (value == null) {
            return fallback;
        }
        if (!(value instanceof Number n) || n.doubleValue() != n.intValue()) {
            problems.add(where + key + " must be a whole number");
            return null;
        }
        if (n.intValue() < min || n.intValue() > max) {
            problems.add(where + key + " must be between " + min + " and " + max);
            return null;
        }
        return n.intValue();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}
