package dev.wayfarer.alloys;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** What template + base + ingot make at the smithing table. */
class SmithingRulesTest {
    private final AlloyRules rules = AlloyFixtures.rules();

    private Optional<AlloyRules.Gear> smith(String template, AlloyRules.SmithBase base, String ingot) {
        return rules.smith(template, base, ingot);
    }

    private static AlloyRules.Gear gear(String alloy, GearShape shape) {
        return new AlloyRules.Gear(alloy, shape);
    }

    @Test
    void steelAndBronzeUpgradeIronGearInTheSameShape() {
        assertEquals(Optional.of(gear("steel", GearShape.PICKAXE)),
                smith("steel", AlloyRules.SmithBase.vanilla("iron_pickaxe"), "steel"));
        assertEquals(Optional.of(gear("bronze", GearShape.BOOTS)),
                smith("bronze", AlloyRules.SmithBase.vanilla("iron_boots"), "bronze"));
        assertEquals(Optional.of(gear("steel", GearShape.SPEAR)),
                smith("steel", AlloyRules.SmithBase.vanilla("iron_spear"), "steel"));
    }

    @Test
    void theOtherAlloysUpgradeDiamondGearAdamantIncluded() {
        for (String alloy : new String[] {"shakudo", "electrum", "hepatizon", "adamant"}) {
            assertEquals(Optional.of(gear(alloy, GearShape.SWORD)),
                    smith(alloy, AlloyRules.SmithBase.vanilla("diamond_sword"), alloy));
            assertEquals(Optional.empty(), smith(alloy, AlloyRules.SmithBase.vanilla("iron_sword"), alloy));
        }
    }

    @Test
    void theWrongVanillaTierIsRefused() {
        assertEquals(Optional.empty(), smith("steel", AlloyRules.SmithBase.vanilla("diamond_pickaxe"), "steel"));
        assertEquals(Optional.empty(), smith("steel", AlloyRules.SmithBase.vanilla("golden_pickaxe"), "steel"));
        assertEquals(Optional.empty(), smith("adamant", AlloyRules.SmithBase.vanilla("netherite_chestplate"), "adamant"));
    }

    @Test
    void anyOtherAlloysGearMovesOntoTheTemplatesAlloyKeepingItsShape() {
        assertEquals(Optional.of(gear("electrum", GearShape.PICKAXE)),
                smith("electrum", AlloyRules.SmithBase.alloy(gear("steel", GearShape.PICKAXE)), "electrum"));
        assertEquals(Optional.of(gear("bronze", GearShape.HELMET)),
                smith("bronze", AlloyRules.SmithBase.alloy(gear("adamant", GearShape.HELMET)), "bronze"));
    }

    @Test
    void theSameAlloyIsNotAnUpgrade() {
        assertEquals(Optional.empty(), smith("steel", AlloyRules.SmithBase.alloy(gear("steel", GearShape.AXE)), "steel"));
    }

    @Test
    void templateAndIngotMustBeTheSameAlloy() {
        assertEquals(Optional.empty(), smith("steel", AlloyRules.SmithBase.vanilla("iron_axe"), "bronze"));
        assertEquals(Optional.empty(), smith("steel", AlloyRules.SmithBase.vanilla("iron_axe"), null));
        assertEquals(Optional.empty(), smith(null, AlloyRules.SmithBase.vanilla("iron_axe"), "steel"));
    }

    @Test
    void nonGearAndUnknownAlloysAreRefused() {
        assertEquals(Optional.empty(), smith("steel", AlloyRules.SmithBase.vanilla("iron_ingot"), "steel"));
        assertEquals(Optional.empty(), smith("steel", AlloyRules.SmithBase.vanilla("bow"), "steel"));
        assertEquals(Optional.empty(), smith("tin", AlloyRules.SmithBase.vanilla("iron_axe"), "tin"));
    }
}
