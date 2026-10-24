package dev.wayfarer.alloys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** alloys.yml: the shipped file loads cleanly, and bad entries are skipped with a problem each. */
class AlloyConfigParserTest {
    private static final AlloyVocabulary KNOWN = new AlloyVocabulary() {
        @Override
        public boolean isItem(String key) {
            return !key.startsWith("nope");
        }

        @Override
        public boolean isBlock(String key) {
            return !key.startsWith("nope");
        }

        @Override
        public boolean isAttribute(String key) {
            return !key.startsWith("nope");
        }
    };

    private static AlloyConfigParser.Result parse(String yaml) {
        return AlloyConfigParser.parse(AlloyFixtures.yaml(yaml), KNOWN);
    }

    @Test
    void shippedConfigLoadsSixAlloysWithoutProblems() {
        AlloyConfigParser.Result r = AlloyFixtures.shipped();
        assertEquals(List.of(), r.problems());
        assertEquals(List.of("steel", "bronze", "shakudo", "electrum", "hepatizon", "adamant"),
                r.rules().alloys().stream().map(AlloyDefinition::id).toList());
        assertTrue(r.rules().anvil().endless());
        assertEquals(30, r.rules().anvil().cap());
        assertEquals(8, r.rules().crucible().maxIngots());
    }

    @Test
    void shippedTemplatesComeFromTheSixGuardians() {
        assertEquals(List.of("bone_marshal", "brood_mother", "sand_pharaoh", "drowned_captain", "piglin_warlord", "void_stalker"),
                AlloyFixtures.rules().alloys().stream().map(a -> a.template().boss()).toList());
    }

    @Test
    void anAlloyWithAnUnknownRecipeItemIsSkipped() {
        AlloyConfigParser.Result r = parse("alloys:\n  tin: {heat: low, recipe: {nope_ore: 2}}\n  ok: {heat: low, recipe: {iron_ingot: 1}}\n");
        assertEquals(List.of("ok"), r.rules().alloys().stream().map(AlloyDefinition::id).toList());
        assertEquals(1, r.problems().size());
        assertTrue(r.problems().get(0).contains("tin") && r.problems().get(0).contains("nope_ore"), r.problems().toString());
    }

    @Test
    void unknownHeatEmptyRecipeAndBadCountsAreSkipped() {
        AlloyConfigParser.Result r = parse("""
                alloys:
                  a: {heat: lukewarm, recipe: {iron_ingot: 1}}
                  b: {heat: low, recipe: {}}
                  c: {heat: low, recipe: {iron_ingot: 0}}
                  d: {heat: low, recipe: {iron_ingot: 1}, material: gold}
                  e: {heat: low, recipe: {iron_ingot: 1}, tools: {durability: -5}}
                  f: {heat: low, recipe: {iron_ingot: 1}, armour: {points: [1, 2]}}
                """);
        assertTrue(r.rules().alloys().isEmpty(), r.rules().alloys().toString());
        assertEquals(6, r.problems().size(), r.problems().toString());
    }

    @Test
    void badBonusesAndRepairItemsAreLeftOutButTheAlloyStays() {
        AlloyConfigParser.Result r = parse("""
                alloys:
                  a:
                    heat: low
                    recipe: {iron_ingot: 1}
                    bonuses:
                      - {attribute: nope_attr, amount: 1}
                      - {attribute: luck}
                      - {attribute: luck, amount: 1, to: elbows}
                      - {attribute: luck, amount: 2, to: armour}
                    repair: {items: [ingot, nope_item]}
                """);
        AlloyDefinition a = r.rules().alloy("a").orElseThrow();
        assertEquals(List.of(new AlloyDefinition.Bonus("luck", 2, false, AlloyDefinition.Applies.ARMOUR)), a.bonuses());
        assertEquals(List.of("ingot"), a.repair().items());
        assertEquals(4, r.problems().size(), r.problems().toString());
    }

    @Test
    void twoAlloysWithTheSameIngredientsAreReported() {
        AlloyConfigParser.Result r = parse("alloys:\n  a: {heat: low, recipe: {iron_ingot: 1}}\n  b: {heat: high, recipe: {iron_ingot: 2}}\n");
        assertEquals(1, r.problems().size(), r.problems().toString());
    }

    @Test
    void unknownHeatBlocksAreSkippedAndSettingsFallBack() {
        AlloyConfigParser.Result r = parse("crucible:\n  heat: {nope_block: low, lava: hot, magma_block: high}\n  max-ingots: 999\nanvil: {cap: 0}\n");
        assertEquals(Map.of("magma_block", Heat.HIGH), r.rules().crucible().heatBlocks());
        assertEquals(8, r.rules().crucible().maxIngots());
        assertEquals(30, r.rules().anvil().cap());
        assertEquals(4, r.problems().size(), r.problems().toString());
    }

    @Test
    void missingSectionsUseDefaults() {
        AlloyConfigParser.Result r = parse("alloys: {}\n");
        assertEquals(List.of(), r.problems());
        assertFalse(r.rules().crucible().heatBlocks().isEmpty());
        assertTrue(r.rules().anvil().endless());
    }
}
