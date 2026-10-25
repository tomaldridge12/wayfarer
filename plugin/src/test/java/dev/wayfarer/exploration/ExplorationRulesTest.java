package dev.wayfarer.exploration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Exploration: prices, cooldown, the chart's target, the safe-surface spawn rule and the needle. */
class ExplorationRulesTest {
    private static ExplorationConfigParser.Result shipped() {
        try (InputStream in = ExplorationRulesTest.class.getResourceAsStream("/exploration.yml")) {
            return ExplorationConfigParser.parse(new Yaml().load(in));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theShippedConfigLoads() {
        ExplorationConfigParser.Result r = shipped();
        assertEquals(List.of(), r.problems());
        assertEquals(3200, r.settings().rangeBlocks());
        assertEquals("Trail Ruins", r.settings().structures().get("minecraft:trail_ruins"));
    }

    @Test
    void rareTargetsCostMore() {
        ExplorationRules.Prices p = shipped().settings().prices();
        assertEquals(315, p.of("minecraft:mansion", true));
        assertEquals(55, p.of("minecraft:igloo", true));
        assertEquals(160, p.of("mushroom_fields", false));
        assertEquals(27, p.of("terralith:alpine_grove", false));
    }

    @Test
    void anUnfoundBiomeCostsTheMultiplierTimesAsMuch() {
        ExplorationRules.Prices p = shipped().settings().prices();
        assertEquals(27, p.biome("terralith:alpine_grove", true));
        assertEquals(54, p.biome("terralith:alpine_grove", false));
        assertEquals(160, p.biome("mushroom_fields", true), "a rare biome keeps its own price");
        assertEquals(320, p.biome("mushroom_fields", false));
        assertEquals(41, new ExplorationRules.Prices(55, 27, Map.of(), 1.5).biome("plains", false), "rounded");
    }

    @Test
    void theBiomeListShowsFoundFirstThenByName() {
        List<ExplorationRules.BiomeOption> out = ExplorationRules.order(List.of(
                new ExplorationRules.BiomeOption("terralith:alpine_grove", "Alpine Grove", false),
                new ExplorationRules.BiomeOption("plains", "Plains", true),
                new ExplorationRules.BiomeOption("badlands", "Badlands", false),
                new ExplorationRules.BiomeOption("desert", "Desert", true)));
        assertEquals(List.of("desert", "plains", "terralith:alpine_grove", "badlands"), out.stream().map(ExplorationRules.BiomeOption::key).toList());
    }

    @Test
    void aFailedSearchChargesNothingButStartsTheCooldown() {
        assertEquals(new ExplorationRules.Settlement(false, true), ExplorationRules.settle(false, false));
        assertEquals(new ExplorationRules.Settlement(true, true), ExplorationRules.settle(true, false));
        assertEquals(new ExplorationRules.Settlement(false, false), ExplorationRules.settle(false, true), "a free pointing is kept");
        assertEquals(new ExplorationRules.Settlement(false, false), ExplorationRules.settle(true, true), "free is never charged");
    }

    @Test
    void aLevelsFileIsReadAsPointsAndNoted() {
        Map<String, Object> v1 = new Yaml().load("""
                version: 1
                wayfinder:
                  structure-levels: 5
                  biome-levels: 3
                  chart-levels: 8
                  rare: {mansion: 15, ancient_city: 12, stronghold: 10}
                """);
        ExplorationConfigParser.Result r = ExplorationConfigParser.parse(v1);
        assertEquals(List.of(), r.problems());
        assertEquals(55, r.settings().prices().structure());
        assertEquals(27, r.settings().prices().biome());
        assertEquals(112, r.settings().chartPoints());
        assertEquals(Map.of("mansion", 315, "ancient_city", 216, "stronghold", 160), r.settings().prices().rare());
        assertEquals(1, r.notes().size());
        assertTrue(r.notes().getFirst().contains("levels"));
    }

    @Test
    void aPointsFileIsReadAsPointsAndBadValuesGiveAProblem() {
        Map<String, Object> v2 = new Yaml().load("""
                version: 2
                wayfinder:
                  structure-points: 70
                  biome-points: oops
                  chart-points: 100
                  unfound-multiplier: 0
                  rare: {mansion: 400, igloo: -1}
                """);
        ExplorationConfigParser.Result r = ExplorationConfigParser.parse(v2);
        assertEquals(70, r.settings().prices().structure());
        assertEquals(27, r.settings().prices().biome(), "a bad value falls back to the default");
        assertEquals(100, r.settings().chartPoints());
        assertEquals(2.0, r.settings().prices().unfoundMultiplier());
        assertEquals(Map.of("mansion", 400), r.settings().prices().rare());
        assertEquals(3, r.problems().size());
        assertEquals(List.of(), r.notes(), "nothing was converted");
    }

    @Test
    void searchesWaitOutTheirCooldown() {
        assertEquals(240_000, ExplorationRules.cooldownLeft(1_000_000, 300, 1_060_000));
        assertEquals(0, ExplorationRules.cooldownLeft(1_000_000, 300, 1_300_000));
        assertEquals(0, ExplorationRules.cooldownLeft(0, 300, 1_000_000), "never searched");
    }

    @Test
    void theChartPointsOnlyAtUnbeatenGuardiansAndPicksTheNearest() {
        Map<String, String> guardians = Map.of("minecraft:trial_chambers", "bone_marshal", "minecraft:ancient_city", "warden_king");
        assertEquals(Set.of("minecraft:ancient_city"), ExplorationRules.unbeaten(guardians, Map.of("defeat:bone_marshal", 2L)));
        assertEquals(Set.of(), ExplorationRules.unbeaten(guardians, Map.of("defeat:bone_marshal", 1L, "defeat:warden_king", 1L)));
        assertEquals(Optional.of("b"), ExplorationRules.nearest(Map.of("a", 900.0, "b", 120.0)));
        assertEquals(Optional.empty(), ExplorationRules.nearest(Map.of()));
    }

    @Test
    void safeSurfaceStopsOnlyNaturalHostileSurfaceSpawnsInTheOverworld() {
        assertTrue(ExplorationRules.stopsSpawn(true, true, true, true, 15, 40, 62), "open sky, even below sea level");
        assertTrue(ExplorationRules.stopsSpawn(true, true, true, true, 0, 70, 62), "above sea level, even under a roof");
        assertFalse(ExplorationRules.stopsSpawn(true, true, true, true, 0, 20, 62), "caves are left alone");
        assertFalse(ExplorationRules.stopsSpawn(false, true, true, true, 15, 70, 62), "off until the dragon falls");
        assertFalse(ExplorationRules.stopsSpawn(true, false, true, true, 15, 70, 62), "other dimensions");
        assertFalse(ExplorationRules.stopsSpawn(true, true, false, true, 15, 70, 62), "spawners and plugins");
        assertFalse(ExplorationRules.stopsSpawn(true, true, true, false, 15, 70, 62), "animals");
    }

    @Test
    void theNeedlePointsTheWayFromWhereYouLook() {
        assertEquals("↑", ExplorationRules.arrow(0, 0, 10), "facing south, target south");
        assertEquals("↓", ExplorationRules.arrow(0, 0, -10));
        assertEquals("→", ExplorationRules.arrow(0, -10, 0), "facing south, west is to the right");
        assertEquals("←", ExplorationRules.arrow(0, 10, 0));
        assertEquals("↑", ExplorationRules.arrow(90, -10, 0), "facing west, target west");
        assertEquals("↗", ExplorationRules.arrow(0, -10, 10));
    }
}
