package dev.wayfarer.alloys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What a crucible's contents melt into, at which heat, for how long. */
class CrucibleRulesTest {
    private final AlloyRules rules = AlloyFixtures.rules();

    private AlloyRules.Melt melt(Map<String, Integer> contents, Heat heat) {
        return rules.melt(contents, Optional.ofNullable(heat));
    }

    @Test
    void oneBatchOfSteelMeltsAtLowHeatInThirtySeconds() {
        AlloyRules.Melt m = melt(Map.of("iron_ingot", 4, "coal_block", 1), Heat.LOW);
        assertEquals(AlloyRules.Status.MELTS, m.status());
        assertEquals("steel", m.alloy().id());
        assertEquals(1, m.batches());
        assertEquals(1, m.ingots());
        assertEquals(30 * 20, m.ticks());
    }

    @Test
    void orderOfLoadingDoesNotMatter() {
        Map<String, Integer> a = new LinkedHashMap<>();
        a.put("coal_block", 1);
        a.put("iron_ingot", 4);
        Map<String, Integer> b = new LinkedHashMap<>();
        b.put("iron_ingot", 4);
        b.put("coal_block", 1);
        assertEquals(melt(a, Heat.LOW), melt(b, Heat.LOW));
    }

    @Test
    void exactMultiplesMeltAsThatManyBatches() {
        AlloyRules.Melt m = melt(Map.of("iron_ingot", 12, "coal_block", 3), Heat.LOW);
        assertEquals(AlloyRules.Status.MELTS, m.status());
        assertEquals(3, m.batches());
        assertEquals(3, m.ingots());
    }

    @Test
    void notAnExactMultipleNeverMelts() {
        assertEquals(AlloyRules.Status.NO_RECIPE, melt(Map.of("iron_ingot", 5, "coal_block", 1), Heat.LOW).status());
        assertEquals(AlloyRules.Status.NO_RECIPE, melt(Map.of("iron_ingot", 8, "coal_block", 1), Heat.LOW).status());
        assertEquals(AlloyRules.Status.NO_RECIPE, melt(Map.of("iron_ingot", 4), Heat.LOW).status());
    }

    @Test
    void leftoversOfAnotherItemStopTheMelt() {
        assertEquals(AlloyRules.Status.NO_RECIPE,
                melt(Map.of("iron_ingot", 4, "coal_block", 1, "dirt", 1), Heat.SOUL).status());
    }

    @Test
    void moreThanEightIngotsInOneMeltDoNotMelt() {
        assertEquals(AlloyRules.Status.MELTS, melt(Map.of("iron_ingot", 32, "coal_block", 8), Heat.LOW).status());
        assertEquals(AlloyRules.Status.NO_RECIPE, melt(Map.of("iron_ingot", 36, "coal_block", 9), Heat.LOW).status());
    }

    @Test
    void hotterHeatMeltsCoolerRecipes() {
        assertEquals(AlloyRules.Status.MELTS, melt(Map.of("iron_ingot", 4, "coal_block", 1), Heat.HIGH).status());
        assertEquals(AlloyRules.Status.MELTS, melt(Map.of("iron_ingot", 4, "coal_block", 1), Heat.SOUL).status());
        assertEquals(AlloyRules.Status.MELTS,
                melt(Map.of("copper_ingot", 4, "gold_ingot", 2, "lapis_lazuli", 2), Heat.SOUL).status());
    }

    @Test
    void coolerHeatRefusesHotterRecipesButNamesTheAlloy() {
        AlloyRules.Melt m = melt(Map.of("copper_ingot", 2, "gold_ingot", 2, "blaze_rod", 1), Heat.HIGH);
        assertEquals(AlloyRules.Status.TOO_COLD, m.status());
        assertEquals("hepatizon", m.alloy().id());
        assertEquals(AlloyRules.Status.TOO_COLD,
                melt(Map.of("diamond", 1, "netherite_scrap", 2, "echo_shard", 1), Heat.LOW).status());
    }

    @Test
    void noHeatRefusesEveryRecipe() {
        assertEquals(AlloyRules.Status.NO_HEAT, melt(Map.of("iron_ingot", 4, "coal_block", 1), null).status());
    }

    @Test
    void emptyContentsAreEmpty() {
        assertEquals(AlloyRules.Status.EMPTY, melt(Map.of(), Heat.LOW).status());
        assertEquals(AlloyRules.Status.EMPTY, melt(Map.of("iron_ingot", 0), Heat.LOW).status());
    }

    @Test
    void meltTimesGrowWithHeat() {
        assertEquals(45 * 20, melt(Map.of("gold_ingot", 4, "prismarine_crystals", 2, "turtle_scute", 1), Heat.HIGH).ticks());
        assertEquals(60 * 20, melt(Map.of("diamond", 2, "netherite_scrap", 4, "echo_shard", 2), Heat.SOUL).ticks());
    }

    @Test
    void ingredientsAreEveryItemAnyRecipeUses() {
        assertTrue(rules.isIngredient("iron_ingot"));
        assertTrue(rules.isIngredient("echo_shard"));
        assertTrue(rules.isIngredient("amethyst_shard"));
        assertFalse(rules.isIngredient("water_bucket"));
        assertFalse(rules.isIngredient("dirt"));
    }

    @Test
    void heatComesFromTheBlockBelowAndCampfiresOnlyWhileLit() {
        assertEquals(Optional.of(Heat.LOW), rules.heatOf("campfire", true));
        assertEquals(Optional.empty(), rules.heatOf("campfire", false));
        assertEquals(Optional.of(Heat.HIGH), rules.heatOf("lava", false));
        assertEquals(Optional.of(Heat.HIGH), rules.heatOf("magma_block", false));
        assertEquals(Optional.of(Heat.SOUL), rules.heatOf("soul_campfire", true));
        assertEquals(Optional.empty(), rules.heatOf("soul_campfire", false));
        assertEquals(Optional.of(Heat.SOUL), rules.heatOf("soul_fire", false));
        assertEquals(Optional.empty(), rules.heatOf("stone", false));
    }
}
