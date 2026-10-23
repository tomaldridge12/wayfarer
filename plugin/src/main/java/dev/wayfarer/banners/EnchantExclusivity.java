package dev.wayfarer.banners;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

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

    /** The excellentenchants: keys used by relic presets, sorted. */
    public static List<String> rewardEnchants(BannerSet set) {
        TreeSet<String> keys = new TreeSet<>();
        set.relics().values().forEach(r -> r.enchants().keySet().stream()
                .filter(k -> k.startsWith("excellentenchants:")).forEach(keys::add));
        return List.copyOf(keys);
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
