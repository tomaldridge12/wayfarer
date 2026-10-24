package dev.wayfarer.collections;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Treasure rules: which site a suspicious block belongs to, whether an eligible brush holds a treasure (chance, a
 * carried curio's boost and the pity ceiling), which treasure (unfound ones three times as likely) and chest rolls.
 * No server types.
 */
public final class TreasureRules {
    /** How much likelier a treasure the player has not found yet is. */
    public static final int UNFOUND_WEIGHT = 3;

    /** What one eligible brush did: whether it holds a treasure and the pity count after it. */
    public record Brush(boolean treasure, int pity) {
    }

    private TreasureRules() {
    }

    /** The site whose loot tables include this one ({@code archaeology/trail_ruins_rare}), or null. */
    public static String site(Map<String, List<String>> sites, String lootTable) {
        for (var e : sites.entrySet()) {
            if (e.getValue().contains(lootTable)) {
                return e.getKey();
            }
        }
        return null;
    }

    /** The brush that the pity ceiling guarantees a treasure on: ceil(1 / chance). */
    public static int ceiling(double chance) {
        return chance <= 0 ? Integer.MAX_VALUE : (int) Math.ceil(1 / chance - 1e-9);
    }

    /**
     * One eligible brush: {@code pity} eligible brushes have gone by without a treasure; a roll under the chance (times
     * the boost) or reaching the ceiling (of the plain chance) gives one and starts the count again.
     */
    public static Brush brush(int pity, double chance, double boost, double roll) {
        int now = pity + 1;
        boolean hit = roll < Math.min(1, chance * boost) || now >= ceiling(chance);
        return new Brush(hit, hit ? 0 : now);
    }

    /** A weighted pick among the treasures of a site, those not yet found three times as likely; null if none. */
    public static TreasureDefinition pick(List<TreasureDefinition> all, String site, Set<String> found, RandomGenerator random) {
        List<TreasureDefinition> here = new ArrayList<>();
        int total = 0;
        for (TreasureDefinition t : all) {
            if (t.brush().contains(site)) {
                here.add(t);
                total += weight(t, found);
            }
        }
        if (total <= 0) {
            return null;
        }
        int roll = random.nextInt(total);
        for (TreasureDefinition t : here) {
            roll -= weight(t, found);
            if (roll < 0) {
                return t;
            }
        }
        return here.getLast();
    }

    private static int weight(TreasureDefinition t, Set<String> found) {
        return t.weight() * (found.contains(t.id()) ? 1 : UNFOUND_WEIGHT);
    }

    /** The treasures a newly filled chest of this loot table gets, each rolled on its own chance. */
    public static List<TreasureDefinition> chest(List<TreasureDefinition> all, String lootTable, RandomGenerator random) {
        List<TreasureDefinition> out = new ArrayList<>();
        for (TreasureDefinition t : all) {
            Double chance = t.chests().get(lootTable);
            if (chance != null && random.nextDouble() < chance) {
                out.add(t);
            }
        }
        return out;
    }

    /** "Brushed at the trail ruins and desert wells; in shipwreck map chests", for an unfound treasure's hint. */
    public static String where(TreasureDefinition t) {
        List<String> parts = new ArrayList<>();
        if (!t.brush().isEmpty()) {
            parts.add("Brushed at " + list(t.brush().stream().map(TreasureRules::siteName).toList()));
        }
        if (!t.chests().isEmpty()) {
            parts.add((parts.isEmpty() ? "In " : "in ") + list(t.chests().keySet().stream().sorted().map(TreasureRules::chestName).toList())
                    + " chests");
        }
        return String.join("; ", parts);
    }

    static String siteName(String site) {
        return switch (site) {
            case "trail_ruins" -> "trail ruins";
            case "desert_well" -> "desert wells";
            case "desert_pyramid" -> "desert pyramids";
            case "ocean_ruin_warm" -> "warm ocean ruins";
            case "ocean_ruin_cold" -> "cold ocean ruins";
            default -> site.replace('_', ' ');
        };
    }

    static String chestName(String lootTable) {
        String path = lootTable.substring(lootTable.lastIndexOf('/') + 1);
        return switch (path) {
            case "shipwreck_map" -> "shipwreck map";
            case "simple_dungeon" -> "dungeon";
            case "village_desert_house" -> "desert village";
            case "village_cartographer" -> "cartographer";
            case "corridor" -> "trial chamber";
            case "bastion_treasure" -> "bastion treasure";
            case "bastion_other", "bastion_bridge" -> "bastion";
            case "end_city_treasure" -> "End city";
            default -> path.replace('_', ' ');
        };
    }

    private static String list(List<String> items) {
        List<String> distinct = items.stream().distinct().toList();
        if (distinct.size() <= 1) {
            return String.join("", distinct);
        }
        return String.join(", ", distinct.subList(0, distinct.size() - 1)) + " and " + distinct.getLast();
    }
}
