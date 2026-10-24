package dev.wayfarer.core;

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
    void aRewardAboveTheServersMaxLevelIsReported() {
        Map<String, String> problems = EnchantExclusivity.levelProblems(
                Map.of("excellentenchants:treefeller", 3, "excellentenchants:veinminer", 3, "excellentenchants:vampire", 1),
                Map.of("excellentenchants:treefeller", 1, "excellentenchants:veinminer", 3));
        assertEquals(Map.of("excellentenchants:treefeller", "MaxLevel: 3 (rewards give level 3, the server allows 1)"), problems);
    }

    @Test
    void ownersMergeAtTheHighestLevelGiven() {
        assertEquals(Map.of("excellentenchants:rage", 2, "excellentenchants:veinminer", 3),
                EnchantExclusivity.merge(List.of(Map.of("excellentenchants:rage", 2),
                        Map.of("excellentenchants:veinminer", 3, "excellentenchants:rage", 1))));
    }
}
