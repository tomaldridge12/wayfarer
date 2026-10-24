package dev.wayfarer.curios;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Turns the gear and supplies sections of curios.yml into presets and supplies; a bad one is skipped with a problem. */
public final class GearSupplyParser {
    static final Set<String> OPENS = Set.of("loot", "crate", "bait", "xp");
    static final Set<String> CRATE_MOBS = Set.of("cow", "pig", "chicken", "sheep");

    /** Names the parser checks against the server. */
    public interface Names {
        boolean isItem(String key);

        /** A namespaced enchantment key that this server has. */
        boolean isEnchant(String key);

        /** A variant ({@code cold}) of a crate mob, or a dye colour for sheep. */
        boolean isVariant(String mob, String variant);
    }

    public record Result(Map<String, GearPreset> gear, Map<String, SupplyDefinition> supplies, List<String> problems,
            List<String> notices) {
    }

    private final Names names;
    private final List<String> problems = new ArrayList<>();
    private final List<String> notices = new ArrayList<>();

    private GearSupplyParser(Names names) {
        this.names = names;
    }

    public static Result parse(Object gear, Object supplies, Names names) {
        GearSupplyParser p = new GearSupplyParser(names);
        Map<String, GearPreset> presets = new LinkedHashMap<>();
        CurioConfigParser.asMap(gear).forEach((id, v) -> {
            try {
                presets.put(id, p.gear(id, CurioConfigParser.asMap(v)));
            } catch (CurioConfigParser.Bad e) {
                p.problems.add("Gear " + id + " skipped: " + e.getMessage());
            }
        });
        Map<String, SupplyDefinition> out = new LinkedHashMap<>();
        CurioConfigParser.asMap(supplies).forEach((id, v) -> {
            try {
                out.put(id, p.supply(id, CurioConfigParser.asMap(v)));
            } catch (CurioConfigParser.Bad e) {
                p.problems.add("Supply " + id + " skipped: " + e.getMessage());
            }
        });
        return new Result(presets, out, List.copyOf(p.problems), List.copyOf(p.notices));
    }

    private GearPreset gear(String id, Map<String, Object> m) throws CurioConfigParser.Bad {
        if (!id.matches("[a-z0-9_]+")) {
            throw new CurioConfigParser.Bad("ids are lower-case letters, digits and _");
        }
        String base = String.valueOf(m.get("base")).toLowerCase(Locale.ROOT);
        String fallback;
        if (base.startsWith("alloy:")) {
            String shape = base.substring(base.lastIndexOf('_') + 1);
            fallback = String.valueOf(m.getOrDefault("fallback", "iron_" + shape)).toLowerCase(Locale.ROOT);
        } else {
            fallback = base;
        }
        if (!names.isItem(fallback)) {
            throw new CurioConfigParser.Bad("base must be alloy:<alloy>_<shape> or a vanilla item (fallback '" + fallback
                    + "' is not an item)");
        }
        Map<String, Integer> enchants = new LinkedHashMap<>();
        for (var e : CurioConfigParser.asMap(m.get("enchants")).entrySet()) {
            String key = e.getKey().toLowerCase(Locale.ROOT);
            key = key.contains(":") ? key : "minecraft:" + key;
            if (!(e.getValue() instanceof Number n) || n.intValue() < 1 || n.intValue() > 10) {
                throw new CurioConfigParser.Bad("enchant " + key + " needs a level from 1 to 10");
            }
            if (!names.isEnchant(key)) {
                if (key.startsWith("minecraft:")) {
                    throw new CurioConfigParser.Bad("enchant " + key + " is not an enchantment");
                }
                notices.add("Gear " + id + ": " + key + " is not on this server; the gear is given without it");
            }
            enchants.put(key, n.intValue());
        }
        Object model = m.get("model");
        if (model != null && base.startsWith("alloy:")) {
            throw new CurioConfigParser.Bad("alloy gear keeps its alloy's look; model is for vanilla bases");
        }
        return new GearPreset(id, String.valueOf(m.getOrDefault("name", CurioConfigParser.title(id))), colour(m.get("colour")),
                String.valueOf(m.getOrDefault("flavour", "")), base, fallback, enchants,
                Boolean.TRUE.equals(m.get("unbreakable")), model == null ? null : String.valueOf(model));
    }

    private SupplyDefinition supply(String id, Map<String, Object> m) throws CurioConfigParser.Bad {
        if (!id.matches("[a-z0-9_]+")) {
            throw new CurioConfigParser.Bad("ids are lower-case letters, digits and _");
        }
        String opens = String.valueOf(m.get("opens")).toLowerCase(Locale.ROOT);
        if (!OPENS.contains(opens)) {
            throw new CurioConfigParser.Bad("opens must be loot, crate, bait or xp");
        }
        List<SupplyRules.Entry> loot = new ArrayList<>();
        List<String> choices = new ArrayList<>();
        int rolls = m.get("rolls") instanceof Number n ? n.intValue() : 1;
        int amount = m.get("amount") instanceof Number n ? n.intValue() : 0;
        switch (opens) {
            case "loot" -> {
                if (!(m.get("loot") instanceof List<?> list) || list.isEmpty()) {
                    throw new CurioConfigParser.Bad("loot lists nothing");
                }
                for (Object o : list) {
                    loot.add(entry(CurioConfigParser.asMap(o)));
                }
                if (rolls < 1 || rolls > 32) {
                    throw new CurioConfigParser.Bad("rolls must be 1 to 32");
                }
            }
            case "crate" -> {
                if (!(m.get("choices") instanceof List<?> list) || list.isEmpty()) {
                    throw new CurioConfigParser.Bad("a crate needs choices such as cow/cold");
                }
                for (Object o : list) {
                    String c = String.valueOf(o).toLowerCase(Locale.ROOT);
                    String[] parts = c.split("/");
                    if (parts.length != 2 || !CRATE_MOBS.contains(parts[0]) || !names.isVariant(parts[0], parts[1])) {
                        throw new CurioConfigParser.Bad("choice '" + o + "' is not cow, pig or chicken/<variant> or sheep/<colour>");
                    }
                    choices.add(c);
                }
            }
            default -> {
                if (amount < 1) {
                    throw new CurioConfigParser.Bad(opens + " needs an amount of 1 or more");
                }
            }
        }
        List<String> description = new ArrayList<>();
        Object d = m.get("description");
        for (Object o : d instanceof List<?> l ? l : d == null ? List.of() : List.of(d)) {
            description.add(String.valueOf(o));
        }
        return new SupplyDefinition(id, String.valueOf(m.getOrDefault("name", CurioConfigParser.title(id))),
                colour(m.get("colour")), description, String.valueOf(m.getOrDefault("flavour", "")), opens, rolls, loot,
                choices, amount);
    }

    private SupplyRules.Entry entry(Map<String, Object> e) throws CurioConfigParser.Bad {
        String item = String.valueOf(e.get("item")).toLowerCase(Locale.ROOT);
        if (!names.isItem(item)) {
            throw new CurioConfigParser.Bad("loot item '" + e.get("item") + "' is not an item");
        }
        String amount = String.valueOf(e.getOrDefault("amount", "1"));
        int min;
        int max;
        try {
            String[] parts = amount.split("-");
            min = Integer.parseInt(parts[0].trim());
            max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : min;
        } catch (NumberFormatException ex) {
            throw new CurioConfigParser.Bad("amount '" + amount + "' must be a number or a range like 2-5");
        }
        int weight = e.get("weight") instanceof Number n ? n.intValue() : 1;
        if (min < 1 || max < min || max > 64 || weight < 1) {
            throw new CurioConfigParser.Bad("loot " + item + " needs amounts from 1 to 64 and a weight of 1 or more");
        }
        return new SupplyRules.Entry(item, min, max, weight);
    }

    private static int colour(Object value) throws CurioConfigParser.Bad {
        if (value == null) {
            return 0xE8D9A8;
        }
        String s = String.valueOf(value);
        if (!s.matches("#[0-9a-fA-F]{6}")) {
            throw new CurioConfigParser.Bad("colour must be #RRGGBB");
        }
        return Integer.parseInt(s.substring(1), 16);
    }
}
