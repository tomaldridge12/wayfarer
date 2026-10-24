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
    private final java.util.Set<String> missingOptional = new java.util.TreeSet<>();

    private BannerConfigParser(Vocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    public static BannerSet parse(Map<String, Object> root, Vocabulary vocabulary) {
        return new BannerConfigParser(vocabulary).run(root);
    }

    private BannerSet run(Map<String, Object> root) {
        Map<String, MobFamily> families = new LinkedHashMap<>(parseFamilies(asMap(root.get("families"))));
        Map<String, Object> bossRoot = asMap(root.get("bosses"));
        Map<String, String> bossBase = new LinkedHashMap<>();
        asMap(bossRoot.get("families")).forEach((boss, value) -> {
            String base = value == null ? "" : String.valueOf(asMap(value).getOrDefault("base", ""));
            if (families.containsKey(boss) || MobFamily.ALL.equals(boss)) {
                problems.add("Boss family " + boss + " skipped: the name is already a mob family");
            } else if (!base.isEmpty() && !families.containsKey(base)) {
                problems.add("Boss family " + boss + ": base family '" + base + "' is not defined; treated as none");
                bossBase.put(boss, "");
                families.put(boss, new MobFamily(boss, java.util.Set.of()));
            } else {
                bossBase.put(boss, base);
                families.put(boss, new MobFamily(boss, java.util.Set.of())); // matched by boss mark, never by mob type
            }
        });
        Map<String, BannerDefinition> banners = new LinkedHashMap<>();
        Map<String, List<BannerSet.Drop>> drops = new LinkedHashMap<>();
        Map<String, Object> raw = asMap(root.get("banners"));
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            BannerDefinition banner = parseBanner(entry.getKey(), entry.getValue(), families);
            if (banner != null) {
                banners.put(banner.id(), banner);
                parseDrops(banner.id(), asMap(entry.getValue()).get("drops"), drops);
            }
        }
        Map<String, HeadDefinition> heads = new LinkedHashMap<>();
        asMap(root.get("heads")).forEach((id, value) -> {
            Ladder l = ladder("Head " + id, value, families, false);
            if (l != null) {
                Object item = l.raw().get("item");
                Object skin = l.raw().get("skin");
                heads.put(id, new HeadDefinition(id, l.name(id), l.family(), l.kills(),
                        item == null ? null : String.valueOf(item).toLowerCase(Locale.ROOT),
                        skin == null ? null : String.valueOf(skin)));
            }
        });
        Map<String, RelicDefinition> relics = new LinkedHashMap<>();
        asMap(root.get("relics")).forEach((id, value) -> {
            RelicDefinition relic = parseRelic(id, value, families);
            if (relic != null) {
                relics.put(id, relic);
            }
        });
        CountingSettings counting = CountingSettings.parse(root.get("counting"), problems);
        BossLoot.Settings loot = parseBossLoot(asMap(bossRoot.get("loot")), Boolean.TRUE.equals(bossRoot.get("count-toward-base-family")));
        Map<String, String> legendaries = new LinkedHashMap<>();
        asMap(bossRoot.get("legendaries")).forEach((boss, relicId) -> {
            String id = String.valueOf(relicId);
            if (!bossBase.containsKey(boss) || !relics.containsKey(id)) {
                problems.add("Boss legendary " + boss + " -> " + id + " skipped: unknown boss family or relic");
            } else {
                legendaries.put(boss, id);
            }
        });
        return new BannerSet(families, banners, heads, relics, counting, drops, List.copyOf(problems),
                List.copyOf(missingOptional), Map.copyOf(bossBase), loot, Map.copyOf(legendaries));
    }

    private BossLoot.Settings parseBossLoot(Map<String, Object> m, boolean countTowardBase) {
        BossLoot.Settings d = BossLoot.Settings.defaults();
        List<Double> ascended = chances(m.get("ascended-chance"), d.ascendedChance(), "bosses.loot.ascended-chance");
        List<Double> legendary = chances(m.get("legendary-chance"), d.legendaryChance(), "bosses.loot.legendary-chance");
        int xp = m.get("xp-per-star") instanceof Number n && n.intValue() >= 0 ? n.intValue() : d.xpPerStar();
        Map<String, List<Integer>> materials = new LinkedHashMap<>();
        Object raw = m.containsKey("materials") ? m.get("materials") : d.materials();
        asMap(raw).forEach((item, range) -> {
            if (vocabulary.isItem(item.toLowerCase(Locale.ROOT)) && range instanceof List<?> r && r.size() == 2
                    && r.get(0) instanceof Number a && r.get(1) instanceof Number b && a.intValue() >= 0) {
                materials.put(item.toLowerCase(Locale.ROOT), List.of(a.intValue(), b.intValue()));
            } else {
                problems.add("bosses.loot.materials." + item + " skipped: needs a known item and [min, max]");
            }
        });
        return new BossLoot.Settings(ascended, legendary, xp, Map.copyOf(materials), countTowardBase);
    }

    private List<Double> chances(Object value, List<Double> fallback, String name) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof List<?> list && list.size() == 5 && list.stream().allMatch(v -> v instanceof Number n
                && n.doubleValue() >= 0 && n.doubleValue() <= 1)) {
            return list.stream().map(v -> ((Number) v).doubleValue()).toList();
        }
        problems.add(name + " must be five chances from 0 to 1; using defaults");
        return fallback;
    }

    /** The fields every ladder reward shares. */
    private record Ladder(Map<String, Object> raw, String family, int kills) {
        String name(String id) {
            return String.valueOf(raw.getOrDefault("name", id));
        }
    }

    /** Family and kill count for a head or relic; null (with a problem) when unusable. */
    private Ladder ladder(String what, Object value, Map<String, MobFamily> families, boolean allowAllMobs) {
        String where = what + " skipped: ";
        if (!(value instanceof Map<?, ?>)) {
            problems.add(where + "not a section");
            return null;
        }
        Map<String, Object> m = asMap(value);
        String family = m.get("family") == null ? null : String.valueOf(m.get("family"));
        boolean all = MobFamily.ALL.equals(family);
        if (family == null || !(families.containsKey(family) || (all && allowAllMobs))) {
            problems.add(where + (family == null ? "missing family"
                    : all ? "the all-mobs count cannot give this reward"
                    : "family '" + family + "' is not defined or was rejected"));
            return null;
        }
        Integer kills = Boolean.TRUE.equals(m.get("drop-only")) ? Integer.valueOf(1)
                : number(m, "kills", -1, 1, Integer.MAX_VALUE, where);
        if (kills == null || kills < 0) {
            if (kills != null) {
                problems.add(where + "missing kills");
            }
            return null;
        }
        return new Ladder(m, family, kills);
    }

    private RelicDefinition parseRelic(String id, Object value, Map<String, MobFamily> families) {
        Ladder l = ladder("Relic " + id, value, families, true);
        if (l == null) {
            return null;
        }
        String where = "Relic " + id + " skipped: ";
        Integer tier = number(l.raw(), "tier", 1, 0, 4, where);
        RelicDefinition.Slot slot;
        try {
            slot = RelicDefinition.Slot.parse(String.valueOf(l.raw().getOrDefault("slot", "weapon")));
        } catch (IllegalArgumentException e) {
            problems.add(where + "slot must be weapon, head, chest, legs, feet or offhand");
            return null;
        }
        if (tier == null) {
            return null;
        }
        String item = l.raw().get("item") == null ? null : String.valueOf(l.raw().get("item")).toLowerCase(Locale.ROOT);
        if (item == null || !vocabulary.isItem(item)) {
            problems.add(where + (item == null ? "missing item" : "unknown item '" + item + "'"));
            return null;
        }
        Integer edge = number(l.raw(), "edge", RelicDefinition.defaultEdge(slot, tier, l.family()), 0, 100, where);
        if (edge == null) {
            return null;
        }
        String trimPattern = null;
        String trimMaterial = null;
        Object trim = l.raw().get("trim");
        if (trim != null) {
            String[] parts = String.valueOf(trim).toLowerCase(Locale.ROOT).split(":");
            if (parts.length == 2 && vocabulary.isTrimPattern(parts[0]) && vocabulary.isTrimMaterial(parts[1])) {
                trimPattern = parts[0];
                trimMaterial = parts[1];
            } else {
                problems.add("Relic " + id + ": trim '" + trim + "' ignored (use pattern:material, e.g. wild:emerald)");
            }
        }
        List<Perk> perks = new ArrayList<>();
        Object rawPerks = l.raw().get("perks");
        for (Object entry : rawPerks instanceof List<?> list ? list : rawPerks == null ? List.of() : List.of(rawPerks)) {
            Object parsed = Perk.parse(entry, families.keySet());
            if (parsed instanceof Perk p) {
                perks.add(p);
            } else {
                problems.add("Relic " + id + ": " + parsed + "; that perk left out");
            }
        }
        Integer dye = null;
        if (l.raw().get("dye") != null) {
            try {
                dye = Integer.parseInt(String.valueOf(l.raw().get("dye")).replace("#", ""), 16);
            } catch (NumberFormatException e) {
                problems.add("Relic " + id + ": dye must be #RRGGBB; ignored");
            }
        }
        int kills = Boolean.TRUE.equals(l.raw().get("drop-only")) ? RelicDefinition.DROP_ONLY : l.kills();
        return new RelicDefinition(id, l.name(id), l.family(), kills, tier, slot, item,
                parseEnchants(id, l.raw().get("enchants")), trimPattern, trimMaterial, edge, List.copyOf(perks), dye);
    }

    /**
     * Preset enchants, `{sharpness: 2, excellentenchants:vampire: 1}`. Unknown enchants (often because
     * ExcellentEnchants is not installed) are left out with a problem; levels above the maximum are lowered.
     */
    private Map<String, Integer> parseEnchants(String relicId, Object value) {
        Map<String, Integer> out = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) {
            return out;
        }
        map.forEach((k, v) -> {
            String key = String.valueOf(k).toLowerCase(Locale.ROOT);
            if (key.indexOf(':') < 0) {
                key = "minecraft:" + key;
            }
            int max = vocabulary.enchantMax(key);
            if (max < 0 && key.startsWith("excellentenchants:") && !vocabulary.hasExcellentEnchants()) {
                missingOptional.add(key); // the optional plugin is absent: one summary warning, not a problem each
            } else if (max < 0) {
                problems.add("Relic " + relicId + ": enchant " + key + " not found"
                        + (key.startsWith("excellentenchants:") ? " (check the name in ExcellentEnchants)" : "") + "; left out");
            } else if (!(v instanceof Number n) || n.intValue() < 1) {
                problems.add("Relic " + relicId + ": enchant " + key + " needs a level of 1 or more; left out");
            } else {
                if (n.intValue() > max) {
                    problems.add("Relic " + relicId + ": " + key + " " + n.intValue() + " lowered to its maximum " + max);
                }
                out.put(key, Math.min(n.intValue(), max));
            }
        });
        return out;
    }

    /** `drops: {wither: 0.5}` gives a 50% chance per kill of that mob type. Bad entries are skipped alone. */
    private void parseDrops(String bannerId, Object value, Map<String, List<BannerSet.Drop>> drops) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Map<?, ?> map)) {
            problems.add("Banner " + bannerId + " drops ignored: must be a map of mob type to chance");
            return;
        }
        map.forEach((mobKey, chanceValue) -> {
            String mob = String.valueOf(mobKey).toLowerCase(Locale.ROOT);
            if (!vocabulary.isMob(mob)) {
                problems.add("Banner " + bannerId + " drop from '" + mobKey + "' skipped: unknown mob type");
            } else if (!(chanceValue instanceof Number n) || n.doubleValue() <= 0 || n.doubleValue() > 1) {
                problems.add("Banner " + bannerId + " drop from " + mob + " skipped: chance must be above 0 and at most 1");
            } else {
                drops.computeIfAbsent(mob, m -> new ArrayList<>()).add(new BannerSet.Drop(bannerId, n.doubleValue()));
            }
        });
    }

    /** A family with an unknown mob, or a mob already in an earlier family, is rejected whole. */
    private Map<String, MobFamily> parseFamilies(Map<String, Object> raw) {
        Map<String, MobFamily> families = new LinkedHashMap<>();
        Map<String, String> owner = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            String id = entry.getKey();
            if (MobFamily.ALL.equals(id)) {
                problems.add("Family " + id + " rejected: the name is reserved for the all-mobs kill count");
                continue;
            }
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
        if (MobFamily.ALL.equals(family)) {
            problems.add(where + "a banner needs a real mob family, not the all-mobs count");
            return null;
        }
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
