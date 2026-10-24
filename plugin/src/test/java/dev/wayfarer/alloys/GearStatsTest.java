package dev.wayfarer.alloys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Each alloy piece's stats and identity, and that no alloy beats netherite outright. */
class GearStatsTest {
    private final AlloyRules rules = AlloyFixtures.rules();

    private AlloyRules.Stats stats(String alloy, GearShape shape) {
        return rules.stats(new AlloyRules.Gear(alloy, shape)).orElseThrow();
    }

    @Test
    void steelIsBuiltOnIronAndVeryDurableButSlightlySlow() {
        AlloyRules.Stats pick = stats("steel", GearShape.PICKAXE);
        assertEquals("iron_pickaxe", pick.item());
        assertEquals(1000, pick.durability());
        assertEquals(5.5, pick.miningSpeed());
        AlloyRules.Stats chest = stats("steel", GearShape.CHESTPLATE);
        assertEquals("iron_chestplate", chest.item());
        assertEquals(6, chest.armour());
        assertEquals(16 * 30, chest.durability());
    }

    @Test
    void swordsAndSpearsDoNotMineButKeepTheDamageBonus() {
        AlloyRules.Stats sword = stats("hepatizon", GearShape.SWORD);
        assertEquals(0, sword.miningSpeed());
        assertEquals(1, sword.damageBonus());
        assertEquals("diamond_spear", stats("hepatizon", GearShape.SPEAR).item());
    }

    @Test
    void adamantIsNetheriteMaterialToughestAndHeavy() {
        AlloyRules.Stats helmet = stats("adamant", GearShape.HELMET);
        assertEquals("netherite_helmet", helmet.item());
        assertEquals(4, helmet.toughness());
        assertEquals(0.1, helmet.knockbackResistance());
        assertEquals(List.of(new AlloyDefinition.Bonus("movement_speed", -0.025, true, AlloyDefinition.Applies.ARMOUR)),
                helmet.bonuses());
        assertEquals(List.of(), stats("adamant", GearShape.PICKAXE).bonuses());
    }

    @Test
    void identityBonusesGoOnlyWhereTheyBelong() {
        assertEquals(List.of("submerged_mining_speed"), names(stats("electrum", GearShape.PICKAXE)));
        assertEquals(List.of(), names(stats("electrum", GearShape.SWORD)));
        assertEquals(List.of("oxygen_bonus", "water_movement_efficiency"), names(stats("electrum", GearShape.BOOTS)));
        assertEquals(List.of("luck"), names(stats("shakudo", GearShape.LEGGINGS)));
        assertEquals(List.of("burning_time"), names(stats("hepatizon", GearShape.HELMET)));
        assertTrue(stats("hepatizon", GearShape.AXE).fireproof());
        assertFalse(stats("steel", GearShape.AXE).fireproof());
    }

    @Test
    void anAlloyConfigNoLongerHasHasNoStats() {
        assertEquals(Optional.empty(), rules.stats(new AlloyRules.Gear("tin", GearShape.SWORD)));
    }

    @Test
    void miningTierFollowsTheStage() {
        assertEquals("iron", rules.alloy("bronze").orElseThrow().material());
        assertEquals("diamond", rules.alloy("shakudo").orElseThrow().material());
        assertEquals("diamond", rules.alloy("electrum").orElseThrow().material());
        assertEquals("diamond", rules.alloy("hepatizon").orElseThrow().material());
        assertEquals("netherite", rules.alloy("adamant").orElseThrow().material());
    }

    /**
     * Netherite: tools 2031 durability, speed 9; armour 20 points, toughness 3. An alloy may beat it in some
     * numbers (that is its identity) but never in all of durability, speed, armour and toughness at once.
     */
    @Test
    void noAlloyBeatsNetheriteOutright() {
        for (AlloyDefinition a : rules.alloys()) {
            int armour = a.armour().points().stream().mapToInt(Integer::intValue).sum();
            boolean beatsAll = a.tools().durability() > 2031 && a.tools().speed() > 9 && armour > 20
                    && a.armour().toughness() > 3;
            assertFalse(beatsAll, a.id() + " beats netherite outright");
            assertTrue(armour <= 20, a.id() + " has more armour points than netherite");
            assertTrue(a.tools().damageBonus() <= 1, a.id() + " hits much harder than its material");
        }
    }

    private static List<String> names(AlloyRules.Stats s) {
        return s.bonuses().stream().map(AlloyDefinition.Bonus::attribute).toList();
    }
}
