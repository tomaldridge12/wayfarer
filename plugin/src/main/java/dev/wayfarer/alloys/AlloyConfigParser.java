package dev.wayfarer.alloys;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Turns alloys.yml into {@link AlloyRules}. A bad alloy is skipped with a problem; the rest still load. */
public final class AlloyConfigParser {
    /** Vanilla tiers an alloy may be built on or upgrade. */
    private static final List<String> TIERS = List.of("iron", "diamond", "netherite");

    private final AlloyVocabulary vocabulary;
    private final List<String> problems = new ArrayList<>();

    private AlloyConfigParser(AlloyVocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    /** Rules plus everything that was wrong with the file. */
    public record Result(AlloyRules rules, List<String> problems) {
    }

    public static Result parse(Map<String, Object> root, AlloyVocabulary vocabulary) {
        return new AlloyConfigParser(vocabulary).run(root);
    }

    private Result run(Map<String, Object> root) {
        AlloyRules.Crucible crucible = parseCrucible(asMap(root.get("crucible")));
        AlloyRules.Anvil anvil = parseAnvil(asMap(root.get("anvil")));
        Map<String, AlloyDefinition> alloys = new LinkedHashMap<>();
        asMap(root.get("alloys")).forEach((id, value) -> {
            AlloyDefinition a = parseAlloy(id, value);
            if (a != null) {
                alloys.put(id, a);
            }
        });
        for (AlloyDefinition a : alloys.values()) {
            for (AlloyDefinition b : alloys.values()) {
                if (a != b && a.recipe().keySet().equals(b.recipe().keySet()) && a.id().compareTo(b.id()) < 0) {
                    problems.add("Alloys " + a.id() + " and " + b.id() + " use the same ingredients; only "
                            + a.id() + " can ever melt");
                }
            }
        }
        return new Result(new AlloyRules(alloys, crucible, anvil), List.copyOf(problems));
    }

    private AlloyRules.Crucible parseCrucible(Map<String, Object> m) {
        AlloyRules.Crucible d = AlloyRules.Crucible.defaults();
        Map<String, Heat> heat = new LinkedHashMap<>();
        if (m.get("heat") == null) {
            heat.putAll(d.heatBlocks());
        }
        asMap(m.get("heat")).forEach((block, value) -> {
            String key = block.toLowerCase(Locale.ROOT);
            Optional<Heat> h = Heat.parse(value);
            if (!vocabulary.isBlock(key) || h.isEmpty()) {
                problems.add("crucible.heat." + block + " skipped: needs a known block and low, high or soul");
            } else {
                heat.put(key, h.get());
            }
        });
        int contents = whole(m, "max-contents", d.maxContents(), 1, 576, "crucible");
        int ingots = whole(m, "max-ingots", d.maxIngots(), 1, 64, "crucible");
        return new AlloyRules.Crucible(Map.copyOf(heat), contents, ingots);
    }

    private AlloyRules.Anvil parseAnvil(Map<String, Object> m) {
        AlloyRules.Anvil d = AlloyRules.Anvil.defaults();
        boolean endless = m.get("endless") instanceof Boolean b ? b : d.endless();
        return new AlloyRules.Anvil(endless, whole(m, "cap", d.cap(), 1, 39, "anvil"));
    }

    private AlloyDefinition parseAlloy(String id, Object value) {
        String where = "Alloy " + id + " skipped: ";
        if (!id.matches("[a-z0-9_]+")) {
            problems.add(where + "ids are lower case letters, digits and _");
            return null;
        }
        if (!(value instanceof Map<?, ?>)) {
            problems.add(where + "not a section");
            return null;
        }
        Map<String, Object> m = asMap(value);
        String name = String.valueOf(m.getOrDefault("name", id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1)));
        int colour = colour(m.get("colour"), id);
        Optional<Heat> heat = Heat.parse(m.get("heat"));
        if (heat.isEmpty()) {
            problems.add(where + "heat must be low, high or soul");
            return null;
        }
        Map<String, Integer> recipe = new LinkedHashMap<>();
        for (var e : asMap(m.get("recipe")).entrySet()) {
            String key = e.getKey().toLowerCase(Locale.ROOT);
            if (!vocabulary.isItem(key)) {
                problems.add(where + "unknown recipe item '" + e.getKey() + "'");
                return null;
            }
            if (!(e.getValue() instanceof Number n) || n.intValue() < 1) {
                problems.add(where + "recipe item " + key + " needs a count of 1 or more");
                return null;
            }
            recipe.put(key, n.intValue());
        }
        if (recipe.isEmpty()) {
            problems.add(where + "recipe is empty");
            return null;
        }
        String material = String.valueOf(m.getOrDefault("material", "iron"));
        String upgrades = String.valueOf(m.getOrDefault("upgrades", material));
        if (!TIERS.contains(material) || !TIERS.contains(upgrades)) {
            problems.add(where + "material and upgrades must be one of " + TIERS);
            return null;
        }
        Integer melt = number(m, "melt-seconds", 30, 1, 3600, where);
        Integer yield = number(m, "yield", 1, 1, 64, where);
        AlloyDefinition.Tools tools = parseTools(asMap(m.get("tools")), where);
        AlloyDefinition.Armour armour = parseArmour(asMap(m.get("armour")), where);
        if (melt == null || yield == null || tools == null || armour == null) {
            return null;
        }
        List<AlloyDefinition.Bonus> bonuses = new ArrayList<>();
        if (m.get("bonuses") instanceof List<?> list) {
            for (Object o : list) {
                AlloyDefinition.Bonus b = parseBonus(id, o);
                if (b != null) {
                    bonuses.add(b);
                }
            }
        }
        AlloyDefinition.Repair repair = parseRepair(id, asMap(m.get("repair")));
        String molten = String.valueOf(m.getOrDefault("molten-block", "orange_concrete")).toLowerCase(Locale.ROOT);
        if (!vocabulary.isBlock(molten)) {
            problems.add("Alloy " + id + ": unknown molten-block '" + molten + "'; using orange_concrete");
            molten = "orange_concrete";
        }
        AlloyDefinition.Template template = parseTemplate(id, asMap(m.get("template")));
        AlloyDefinition.Luck luck = AlloyDefinition.Luck.NONE;
        if (m.get("luck") instanceof Map<?, ?>) {
            Map<String, Object> l = asMap(m.get("luck"));
            int fortune = whole(l, "fortune-bonus", 0, 0, 3, "Alloy " + id + " luck");
            double loot = fraction(l, "extra-loot-chance", 0, "Alloy " + id + " luck");
            luck = new AlloyDefinition.Luck(fortune, loot);
        }
        boolean fireproof = Boolean.TRUE.equals(m.get("fireproof"));
        return new AlloyDefinition(id, name, colour, heat.get(), melt, java.util.Collections.unmodifiableMap(recipe), yield, material, upgrades,
                tools, armour, List.copyOf(bonuses), repair, molten, template, luck, fireproof);
    }

    private AlloyDefinition.Tools parseTools(Map<String, Object> m, String where) {
        Integer durability = number(m, "durability", 250, 1, 100_000, where + "tools ");
        Double speed = decimal(m, "speed", 6, 0.1, 100, where + "tools ");
        Double damage = decimal(m, "damage-bonus", 0, -10, 20, where + "tools ");
        Integer ench = number(m, "enchantability", 14, 1, 100, where + "tools ");
        return durability == null || speed == null || damage == null || ench == null ? null
                : new AlloyDefinition.Tools(durability, speed, damage, ench);
    }

    private AlloyDefinition.Armour parseArmour(Map<String, Object> m, String where) {
        List<Integer> points = List.of(2, 6, 5, 2);
        if (m.get("points") != null) {
            if (m.get("points") instanceof List<?> l && l.size() == 4
                    && l.stream().allMatch(v -> v instanceof Number n && n.intValue() >= 0 && n.intValue() <= 30)) {
                points = l.stream().map(v -> ((Number) v).intValue()).toList();
            } else {
                problems.add(where + "armour.points must be four numbers (helmet, chestplate, leggings, boots)");
                return null;
            }
        }
        Double toughness = decimal(m, "toughness", 0, 0, 20, where + "armour ");
        Double kb = decimal(m, "knockback-resistance", 0, 0, 1, where + "armour ");
        Integer durability = number(m, "durability", 15, 1, 1000, where + "armour ");
        Integer ench = number(m, "enchantability", 9, 1, 100, where + "armour ");
        return toughness == null || kb == null || durability == null || ench == null ? null
                : new AlloyDefinition.Armour(points, toughness, kb, durability, ench);
    }

    private AlloyDefinition.Bonus parseBonus(String id, Object o) {
        String where = "Alloy " + id + ": bonus ";
        if (!(o instanceof Map<?, ?>)) {
            problems.add(where + "is not a section; left out");
            return null;
        }
        Map<String, Object> m = asMap(o);
        String attribute = String.valueOf(m.get("attribute")).toLowerCase(Locale.ROOT);
        if (!vocabulary.isAttribute(attribute)) {
            problems.add(where + "'" + attribute + "' is not an attribute; left out");
            return null;
        }
        if (!(m.get("amount") instanceof Number amount)) {
            problems.add(where + attribute + " needs an amount; left out");
            return null;
        }
        AlloyDefinition.Applies on;
        try {
            on = AlloyDefinition.Applies.valueOf(String.valueOf(m.getOrDefault("to", "all")).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            problems.add(where + attribute + ": to must be held, tools, weapons, armour or all; left out");
            return null;
        }
        boolean multiply = "multiply".equals(m.get("operation"));
        return new AlloyDefinition.Bonus(attribute, amount.doubleValue(), multiply, on);
    }

    private AlloyDefinition.Repair parseRepair(String id, Map<String, Object> m) {
        List<String> items = new ArrayList<>();
        Object raw = m.getOrDefault("items", List.of(AlloyDefinition.Repair.OWN_INGOT));
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                String key = String.valueOf(o).toLowerCase(Locale.ROOT);
                if (key.equals(AlloyDefinition.Repair.OWN_INGOT) || vocabulary.isItem(key)) {
                    items.add(key);
                } else {
                    problems.add("Alloy " + id + ": repair item '" + o + "' is not an item; left out");
                }
            }
        }
        return new AlloyDefinition.Repair(List.copyOf(items), fraction(m, "share", 0.25, "Alloy " + id + " repair"));
    }

    private AlloyDefinition.Template parseTemplate(String id, Map<String, Object> m) {
        String boss = m.get("boss") == null ? null : String.valueOf(m.get("boss"));
        String block = String.valueOf(m.getOrDefault("duplicate-block", "cobblestone")).toLowerCase(Locale.ROOT);
        if (!vocabulary.isItem(block)) {
            problems.add("Alloy " + id + ": unknown template duplicate-block '" + block + "'; using cobblestone");
            block = "cobblestone";
        }
        return new AlloyDefinition.Template(boss, fraction(m, "chance", 1.0, "Alloy " + id + " template"), block);
    }

    private int colour(Object value, String id) {
        if (value == null) {
            return 0xFFFFFF;
        }
        String text = String.valueOf(value);
        if (text.matches("#[0-9a-fA-F]{6}")) {
            return Integer.parseInt(text.substring(1), 16);
        }
        problems.add("Alloy " + id + ": colour must be #RRGGBB; using white");
        return 0xFFFFFF;
    }

    private Integer number(Map<String, Object> m, String key, int fallback, int min, int max, String where) {
        Object v = m.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && n.intValue() >= min && n.intValue() <= max) {
            return n.intValue();
        }
        problems.add(where + key + " must be a whole number from " + min + " to " + max);
        return null;
    }

    private Double decimal(Map<String, Object> m, String key, double fallback, double min, double max, String where) {
        Object v = m.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n && n.doubleValue() >= min && n.doubleValue() <= max) {
            return n.doubleValue();
        }
        problems.add(where + key + " must be a number from " + min + " to " + max);
        return null;
    }

    /** A setting that falls back with a problem instead of skipping its section. */
    private int whole(Map<String, Object> m, String key, int fallback, int min, int max, String where) {
        Integer n = number(m, key, fallback, min, max, where + ".");
        return n == null ? fallback : n;
    }

    private double fraction(Map<String, Object> m, String key, double fallback, String where) {
        Double d = decimal(m, key, fallback, 0, 1, where + ".");
        return d == null ? fallback : d;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
            return out;
        }
        return Map.of();
    }
}
