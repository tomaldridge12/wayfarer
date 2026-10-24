package dev.wayfarer.alloys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

/** What repairs alloy gear on an anvil, how much, and what the anvil charges. */
class RepairRulesTest {
    private final AlloyRules rules = AlloyFixtures.rules();

    @Test
    void alloyGearRepairsWithItsOwnIngotAQuarterAtATime() {
        assertEquals(OptionalDouble.of(0.25), rules.repairShare("electrum", "electrum", null));
        assertEquals(OptionalDouble.of(0.25), rules.repairShare("adamant", "adamant", null));
    }

    @Test
    void steelIsSlowToRepair() {
        assertEquals(OptionalDouble.of(0.125), rules.repairShare("steel", "steel", null));
    }

    @Test
    void bronzeAlsoRepairsWithPlainCopper() {
        assertEquals(OptionalDouble.of(0.25), rules.repairShare("bronze", null, "copper_ingot"));
        assertEquals(OptionalDouble.of(0.25), rules.repairShare("bronze", "bronze", null));
    }

    @Test
    void vanillaIngotsAndOtherAlloysNeverRepair() {
        assertTrue(rules.repairShare("steel", null, "iron_ingot").isEmpty());
        assertTrue(rules.repairShare("adamant", null, "netherite_ingot").isEmpty());
        assertTrue(rules.repairShare("steel", "bronze", null).isEmpty());
        assertTrue(rules.repairShare("electrum", null, "copper_ingot").isEmpty());
        assertTrue(rules.repairShare("tin", "tin", null).isEmpty());
    }

    @Test
    void aRepairUsesOnlyTheItemsTheDamageNeeds() {
        assertEquals(new AlloyRules.RepairPlan(2, 0), AlloyRules.repair(1000, 400, 10, 0.25));
        assertEquals(new AlloyRules.RepairPlan(3, 125), AlloyRules.repair(1000, 500, 3, 0.125));
        assertEquals(new AlloyRules.RepairPlan(0, 0), AlloyRules.repair(1000, 0, 5, 0.25));
        assertEquals(new AlloyRules.RepairPlan(1, 0), AlloyRules.repair(1000, 1, 64, 0.25));
    }

    @Test
    void repairCostIsPriorWorkPlusAnItemEachPlusTheRename() {
        assertEquals(3, AlloyRules.repairCost(0, 2, true));
        assertEquals(15, AlloyRules.repairCost(15, 0, false));
    }

    @Test
    void endlessRepairCapsTheCostAndLeavesCheaperCostsAlone() {
        assertEquals(30, rules.anvilCost(39));
        assertEquals(30, rules.anvilCost(120));
        assertEquals(12, rules.anvilCost(12));
    }

    @Test
    void withEndlessRepairOffTheCostIsVanillas() {
        AlloyRules off = AlloyConfigParser.parse(AlloyFixtures.yaml("anvil: {endless: false, cap: 30}\n"), AlloyVocabulary.ANY).rules();
        assertEquals(120, off.anvilCost(120));
    }
}
