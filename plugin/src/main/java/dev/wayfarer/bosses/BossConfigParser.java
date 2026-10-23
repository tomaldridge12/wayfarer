package dev.wayfarer.bosses;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Turns bosses.yml into a roster. Bad bosses or variants are skipped with a message; the rest still load. */
public final class BossConfigParser {
    private static final Set<String> SLOTS = Set.of("head", "chest", "legs", "feet", "hand", "offhand");

    private final BossVocabulary vocabulary;
    private final List<String> problems = new ArrayList<>();
    private final List<String> notices = new ArrayList<>();

    private BossConfigParser(BossVocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    public static BossRoster parse(Map<String, Object> root, BossVocabulary vocabulary) {
        return new BossConfigParser(vocabulary).run(root);
    }

    private BossRoster run(Map<String, Object> root) {
        StarScale stars = parseStars(asMap(root.get("stars")));
        Map<String, BossDefinition> bosses = new LinkedHashMap<>();
        asMap(root.get("bosses")).forEach((id, value) -> {
            BossDefinition boss = parseBoss(id, value);
            if (boss != null) {
                bosses.put(id, boss);
            }
        });
        return new BossRoster(bosses, stars, parseSpawning(asMap(root.get("spawning"))), List.copyOf(problems),
                List.copyOf(notices));
    }

    private SpawnRules.Settings parseSpawning(Map<String, Object> m) {
        SpawnRules.Settings d = SpawnRules.Settings.defaults();
        List<Integer> bands = d.starBands();
        if (m.get("star-distances") instanceof List<?> list && list.stream().allMatch(v -> v instanceof Number)) {
            bands = list.stream().map(v -> ((Number) v).intValue()).sorted().toList();
        } else if (m.get("star-distances") != null) {
            problems.add("spawning.star-distances must be a list of block distances; using defaults");
        }
        int min = m.get("guardian-min-stars") instanceof Number n ? StarScale.clamp(n.intValue()) : d.guardianMinStars();
        double extra = m.get("extra-star-chance") instanceof Number n ? Math.max(0, Math.min(1, n.doubleValue()))
                : d.extraStarChance();
        int naturalMax = m.get("natural-max-stars") instanceof Number n ? StarScale.clamp(n.intValue()) : d.naturalMaxStars();
        int naturalCooldown = m.get("natural-cooldown-minutes") instanceof Number n && n.intValue() >= 0 ? n.intValue()
                : d.naturalCooldownMinutes();
        return new SpawnRules.Settings(bands, min, extra, naturalMax, naturalCooldown);
    }

    private StarScale parseStars(Map<String, Object> m) {
        StarScale d = StarScale.defaults();
        return new StarScale(multipliers(m.get("health"), d.health(), "stars.health"),
                multipliers(m.get("damage"), d.damage(), "stars.damage"));
    }

    private List<Double> multipliers(Object value, List<Double> fallback, String name) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof List<?> list && list.size() == StarScale.MAX
                && list.stream().allMatch(v -> v instanceof Number n && n.doubleValue() > 0)) {
            return list.stream().map(v -> ((Number) v).doubleValue()).toList();
        }
        problems.add(name + " must be a list of " + StarScale.MAX + " numbers above 0; using defaults");
        return fallback;
    }

    private BossDefinition parseBoss(String id, Object value) {
        String where = "Boss " + id + " skipped: ";
        if (!(value instanceof Map<?, ?>)) {
            problems.add(where + "not a section");
            return null;
        }
        Map<String, Object> m = asMap(value);
        Double health = positive(m.get("health"), where + "health");
        Double damage = positive(m.get("damage"), where + "damage");
        if (health == null || damage == null) {
            return null;
        }
        int cooldown = m.get("cooldown-minutes") instanceof Number n && n.intValue() >= 0 ? n.intValue() : 60;
        List<BossVariant> variants = new ArrayList<>();
        asMap(m.get("variants")).forEach((vid, v) -> {
            BossVariant variant = parseVariant(id, vid, v);
            if (variant != null) {
                variants.add(variant);
            }
        });
        if (variants.isEmpty()) {
            problems.add(where + "no usable variants");
            return null;
        }
        return new BossDefinition(id, String.valueOf(m.getOrDefault("name", id)), health, damage,
                List.copyOf(variants), cooldown, parseSpawn(id, m.get("spawn"), variants.getFirst().entity()));
    }

    private SpawnRules.BossSpawn parseSpawn(String bossId, Object value, String defaultEntity) {
        if (!(value instanceof Map<?, ?>)) {
            return SpawnRules.BossSpawn.none();
        }
        Map<String, Object> m = asMap(value);
        String entity = String.valueOf(m.getOrDefault("entity", defaultEntity)).toLowerCase(Locale.ROOT);
        boolean any = "any".equals(m.get("biomes"));
        java.util.Set<String> biomes = new java.util.LinkedHashSet<>();
        if (m.get("biomes") instanceof List<?> list) {
            for (Object b : list) {
                String entry = String.valueOf(b).toLowerCase(Locale.ROOT);
                if (entry.startsWith("#")) {
                    String tag = fullKey(entry.substring(1));
                    java.util.Set<String> members = vocabulary.biomeTag(tag);
                    if (members == null) {
                        notices.add("Boss " + bossId + ": biome tag #" + tag + " is not on this server; skipped");
                    } else {
                        biomes.addAll(members);
                    }
                } else if (vocabulary.isBiome(fullKey(entry))) {
                    biomes.add(fullKey(entry));
                } else {
                    problems.add("Boss " + bossId + ": unknown biome '" + b + "' ignored");
                }
            }
        }
        java.util.Set<String> structures = new java.util.LinkedHashSet<>();
        if (m.get("structures") instanceof List<?> list) {
            for (Object st : list) {
                String structure = fullKey(String.valueOf(st).toLowerCase(Locale.ROOT));
                if (vocabulary.isStructure(structure)) {
                    structures.add(structure);
                } else {
                    problems.add("Boss " + bossId + ": unknown structure '" + st + "' ignored");
                }
            }
        }
        double chance = m.get("chance") instanceof Number n ? Math.max(0, Math.min(1, n.doubleValue())) : 0;
        boolean night = Boolean.TRUE.equals(m.get("night-only"));
        return new SpawnRules.BossSpawn(entity, java.util.Set.copyOf(biomes), any, chance, night,
                java.util.Set.copyOf(structures));
    }

    private BossVariant parseVariant(String bossId, String id, Object value) {
        String where = "Boss " + bossId + " variant " + id + " skipped: ";
        if (!(value instanceof Map<?, ?>)) {
            problems.add(where + "not a section");
            return null;
        }
        Map<String, Object> m = asMap(value);
        String entity = String.valueOf(m.getOrDefault("entity", "")).toLowerCase(Locale.ROOT);
        if (!vocabulary.isBossEntity(entity)) {
            problems.add(where + "unknown or unsuitable entity '" + entity + "'");
            return null;
        }
        String glow = String.valueOf(m.getOrDefault("glow", "white")).toLowerCase(Locale.ROOT);
        if (!vocabulary.isGlowColour(glow)) {
            problems.add("Boss " + bossId + " variant " + id + ": unknown glow colour '" + glow + "'; using white");
            glow = "white";
        }
        double scale = m.get("scale") instanceof Number n && n.doubleValue() > 0 && n.doubleValue() <= 4 ? n.doubleValue() : 1.0;
        Map<String, String> equipment = new LinkedHashMap<>();
        asMap(m.get("equipment")).forEach((slot, item) -> {
            String s = slot.toLowerCase(Locale.ROOT);
            String i = String.valueOf(item).toLowerCase(Locale.ROOT);
            if (SLOTS.contains(s) && vocabulary.isItem(i)) {
                equipment.put(s, i);
            } else {
                problems.add("Boss " + bossId + " variant " + id + ": equipment " + slot + ": " + item + " ignored");
            }
        });
        List<Ability> abilities = new ArrayList<>();
        if (m.get("abilities") instanceof List<?> list) {
            for (Object a : list) {
                Ability.parse(String.valueOf(a)).ifPresentOrElse(abilities::add,
                        () -> problems.add("Boss " + bossId + " variant " + id + ": unknown ability '" + a + "' ignored"));
            }
        }
        int[] colours = colours(m.get("colours"));
        return new BossVariant(id, String.valueOf(m.getOrDefault("name", id)), entity, glow, scale,
                Map.copyOf(equipment), List.copyOf(abilities), colours[0], colours[1]);
    }

    /** "#RRGGBB" pair for the name gradient; a warm default when absent or bad. */
    private static int[] colours(Object value) {
        if (value instanceof List<?> list && list.size() == 2) {
            try {
                return new int[] {Integer.parseInt(String.valueOf(list.get(0)).replace("#", ""), 16),
                        Integer.parseInt(String.valueOf(list.get(1)).replace("#", ""), 16)};
            } catch (NumberFormatException ignored) {
                // fall through to the default
            }
        }
        return new int[] {0xFFE7A0, 0xFF5E3A};
    }

    /** "plains" means "minecraft:plains"; "terralith:desert_canyon" stays as written. */
    static String fullKey(String name) {
        return name.indexOf(':') < 0 ? "minecraft:" + name : name;
    }

    private Double positive(Object value, String name) {
        if (value instanceof Number n && n.doubleValue() > 0) {
            return n.doubleValue();
        }
        problems.add(name + " must be a number above 0");
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
    }
}
