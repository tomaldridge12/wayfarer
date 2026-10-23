package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnchantExclusivityTest {
    @Test
    void exclusiveEnchantsNeedNoChanges() {
        var exclusive = new EnchantExclusivity.Distribution(true, false, false, false, false, false);
        assertEquals(Map.of(), EnchantExclusivity.problems(Map.of("excellentenchants:vampire", exclusive)));
    }

    @Test
    void eachLeakNamesTheSettingToChange() {
        var vanillaLike = new EnchantExclusivity.Distribution(false, true, true, true, true, true);
        Map<String, EnchantExclusivity.Distribution> reported = new LinkedHashMap<>();
        reported.put("excellentenchants:vampire", vanillaLike);
        assertEquals(Map.of("excellentenchants:vampire", List.of("Treasure: true", "Tradeable: false",
                        "On_Mob_Spawn_Equipment: false", "On_Traded_Equipment: false", "On_Random_Loot: false",
                        "Discoverable: false")),
                EnchantExclusivity.problems(reported));
    }

    @Test
    void rewardEnchantsComeFromRelicPresets() {
        RelicDefinition cleaver = new RelicDefinition("zombie_weapon", "Cleaver", "zombie", 400, 1,
                RelicDefinition.Slot.WEAPON, "iron_axe",
                Map.of("minecraft:sharpness", 2, "excellentenchants:vampire", 1, "excellentenchants:rage", 1),
                null, null, 30, List.of());
        BannerSet set = new BannerSet(Map.of(), Map.of(), Map.of(), Map.of("zombie_weapon", cleaver),
                CountingSettings.defaults(), Map.of(), List.of());
        assertEquals(List.of("excellentenchants:rage", "excellentenchants:vampire"), EnchantExclusivity.rewardEnchants(set));
    }
}
