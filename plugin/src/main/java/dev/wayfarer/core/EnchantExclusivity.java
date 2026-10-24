package dev.wayfarer.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which reward enchants ExcellentEnchants still hands out elsewhere. The six flags are its per-enchant
 * distribution settings; a reward enchant should be treasure and none of the other five. No server types.
 */
public final class EnchantExclusivity {
    /** One enchant's distribution as ExcellentEnchants reports it. */
    public record Distribution(boolean treasure, boolean tradable, boolean onMobSpawnEquipment,
            boolean onTradedEquipment, boolean onRandomLoot, boolean discoverable) {
        /** The config settings to change, named as in the enchant's file under ExcellentEnchants/enchants/. */
        List<String> fixes() {
            List<String> out = new ArrayList<>();
            if (!treasure) {
                out.add("Treasure: true");
            }
            if (tradable) {
                out.add("Tradeable: false");
            }
            if (onMobSpawnEquipment) {
                out.add("On_Mob_Spawn_Equipment: false");
            }
            if (onTradedEquipment) {
                out.add("On_Traded_Equipment: false");
            }
            if (onRandomLoot) {
                out.add("On_Random_Loot: false");
            }
            if (discoverable) {
                out.add("Discoverable: false");
            }
            return out;
        }
    }

    private EnchantExclusivity() {
    }

    /**
     * Reward enchants given at a higher level than ExcellentEnchants allows ({@code max}: key -> its max level, missing
     * when unknown), each with the fix.
     */
    public static Map<String, String> levelProblems(Map<String, Integer> given, Map<String, Integer> max) {
        Map<String, String> out = new LinkedHashMap<>();
        new java.util.TreeMap<>(given).forEach((key, level) -> {
            Integer allowed = max.get(key);
            if (allowed != null && level > allowed) {
                out.put(key, "MaxLevel: " + level + " (rewards give level " + level + ", the server allows " + allowed + ")");
            }
        });
        return out;
    }

    /** Several owners' reward enchants as one map: each key at the highest level any owner gives. */
    public static Map<String, Integer> merge(java.util.Collection<Map<String, Integer>> owners) {
        Map<String, Integer> out = new java.util.TreeMap<>();
        owners.forEach(m -> m.forEach((k, v) -> out.merge(k, v, Math::max)));
        return out;
    }

    /** For each reward enchant that is not exclusive, the settings to change. */
    public static Map<String, List<String>> problems(Map<String, Distribution> reported) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        reported.forEach((key, d) -> {
            List<String> fixes = d.fixes();
            if (!fixes.isEmpty()) {
                out.put(key, fixes);
            }
        });
        return out;
    }
}
