package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

class BossLootTest {
    private static DoubleSupplier fixed(double... values) {
        Deque<Double> q = new ArrayDeque<>();
        for (double v : values) {
            q.add(v);
        }
        return q::removeFirst;
    }

    @Test
    void chancesRiseWithStarsAndAscendedOnlyFromMiniBosses() {
        BossLoot.Settings s = BossLoot.Settings.defaults();
        assertEquals(new BossLoot.Rolls(true, false), BossLoot.roll(s, 1, true, fixed(0.04, 0.5)));
        assertEquals(new BossLoot.Rolls(false, false), BossLoot.roll(s, 1, true, fixed(0.06, 0.5)));
        assertEquals(new BossLoot.Rolls(true, true), BossLoot.roll(s, 5, true, fixed(0.34, 0.079)));
        assertEquals(new BossLoot.Rolls(false, true), BossLoot.roll(s, 5, false, fixed(0.079)));
    }

    @Test
    void materialsScaleWithStars() {
        assertEquals(2, BossLoot.materialAmount(List.of(2, 4), 1, () -> 0.0));
        assertEquals(20, BossLoot.materialAmount(List.of(2, 4), 5, () -> 0.99));
    }

    private static RelicDefinition relic(String id, String family, int tier) {
        return new RelicDefinition(id, id, family, RelicDefinition.DROP_ONLY, tier, RelicDefinition.Slot.WEAPON);
    }

    @Test
    void ascendedPicksAmongTheBaseFamilysAscendedRelics() {
        BannerSet set = new BannerSet(Map.of(), Map.of(), Map.of(), Map.of(
                "zw3", relic("zw3", "zombie", 3), "za3", relic("za3", "zombie", 3), "zw2", relic("zw2", "zombie", 2),
                "sw3", relic("sw3", "spider", 3)), CountingSettings.defaults(), Map.of(), List.of());
        String picked = BossLoot.pickAscended(set, "zombie", () -> 0.0).orElseThrow().id();
        assertTrue(picked.equals("zw3") || picked.equals("za3"));
        assertTrue(BossLoot.pickAscended(set, "creeper", () -> 0.0).isEmpty());
    }
}
