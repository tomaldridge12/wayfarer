package dev.wayfarer.bosses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SpawnRulesTest {
    private static final SpawnRules.BossSpawn WARLORD = new SpawnRules.BossSpawn("zombie", Set.of("plains"), false,
            0.001, true, Set.of());
    private static final SpawnRules.BossSpawn PHARAOH = new SpawnRules.BossSpawn("husk", Set.of("desert"), false,
            0.001, false, Set.of("desert_pyramid"));

    @Test
    void naturalSpawnNeedsEntityBiomeNightRoomAndLuck() {
        assertTrue(SpawnRules.naturalSpawn(WARLORD, "zombie", "plains", true, false, 0, () -> 0.0005));
        assertFalse(SpawnRules.naturalSpawn(WARLORD, "zombie", "plains", true, false, 0, () -> 0.002)); // unlucky
        assertFalse(SpawnRules.naturalSpawn(WARLORD, "skeleton", "plains", true, false, 0, () -> 0)); // wrong mob
        assertFalse(SpawnRules.naturalSpawn(WARLORD, "zombie", "desert", true, false, 0, () -> 0)); // wrong biome
        assertFalse(SpawnRules.naturalSpawn(WARLORD, "zombie", "plains", false, false, 0, () -> 0)); // daytime
        assertFalse(SpawnRules.naturalSpawn(WARLORD, "zombie", "plains", true, true, 0, () -> 0)); // one alive
        assertFalse(SpawnRules.naturalSpawn(WARLORD, "zombie", "plains", true, false, 5000, () -> 0)); // cooling
    }

    @Test
    void anyBiomeMeansAnywhere() {
        SpawnRules.BossSpawn prophet = new SpawnRules.BossSpawn("creeper", Set.of(), true, 0.001, true, Set.of());
        assertTrue(SpawnRules.naturalSpawn(prophet, "creeper", "badlands", true, false, 0, () -> 0));
        assertFalse(SpawnRules.naturalSpawn(SpawnRules.BossSpawn.none(), "creeper", "badlands", true, false, 0, () -> 0));
    }

    @Test
    void guardiansNeedTheirStructureAndBothCooldowns() {
        assertTrue(SpawnRules.guardian(PHARAOH, "desert_pyramid", false, 0, 0));
        assertFalse(SpawnRules.guardian(PHARAOH, "jungle_pyramid", false, 0, 0));
        assertFalse(SpawnRules.guardian(PHARAOH, "desert_pyramid", true, 0, 0));
        assertFalse(SpawnRules.guardian(PHARAOH, "desert_pyramid", false, 1, 0));
        assertFalse(SpawnRules.guardian(PHARAOH, "desert_pyramid", false, 0, 1));
    }

    @Test
    void starsRiseWithDistanceAndGuardiansStartAtThree() {
        SpawnRules.Settings s = SpawnRules.Settings.defaults();
        assertEquals(1, SpawnRules.stars(100, false, s, () -> 0.9));
        assertEquals(2, SpawnRules.stars(100, false, s, () -> 0.1)); // the occasional extra star
        assertEquals(3, SpawnRules.stars(2500, false, s, () -> 0.9));
        assertEquals(5, SpawnRules.stars(9000, false, s, () -> 0.9));
        assertEquals(5, SpawnRules.stars(9000, false, s, () -> 0.1)); // capped at five
        assertEquals(3, SpawnRules.stars(100, true, s, () -> 0.9));
    }

    @Test
    void cooldownsCountDown() {
        assertEquals(60_000, SpawnRules.cooldownLeft(1_000_000, 2, 1_060_000));
        assertEquals(0, SpawnRules.cooldownLeft(1_000_000, 2, 1_200_000));
        assertEquals(0, SpawnRules.cooldownLeft(0, 60, 1_800_000_000_000L)); // never spawned
    }
}
