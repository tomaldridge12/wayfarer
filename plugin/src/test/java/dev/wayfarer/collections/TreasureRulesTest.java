package dev.wayfarer.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Treasures: the shipped list, sites, chance and pity, the unfound bias, chest rolls and hints. */
class TreasureRulesTest {
    private static final TreasureConfigParser.Result SHIPPED = TreasureConfigParser.parse(CollectionConfigParser
            .parse(CollectionsTest.shipped()).collections().get("treasures").settings());

    private static TreasureDefinition treasure(String id) {
        return SHIPPED.treasures().stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theShippedTreasuresLoad() {
        assertEquals(List.of(), SHIPPED.problems());
        assertEquals(19, SHIPPED.treasures().size());
        assertEquals(0.12, SHIPPED.settings().brushChance());
        assertEquals("minecraft:shipwreck", treasure("shipwreck_chart").chart());
        SHIPPED.treasures().forEach(t -> assertFalse(t.flavour().isBlank(), t.id()));
    }

    @Test
    void sitesFollowTheArchaeologyLootTable() {
        assertEquals("trail_ruins", TreasureRules.site(SHIPPED.settings().sites(), "archaeology/trail_ruins_rare"));
        assertEquals("desert_well", TreasureRules.site(SHIPPED.settings().sites(), "archaeology/desert_well"));
        assertNull(TreasureRules.site(SHIPPED.settings().sites(), "chests/simple_dungeon"));
    }

    @Test
    void pityGuaranteesATreasureByTheCeilingOfThePlainChance() {
        assertEquals(9, TreasureRules.ceiling(0.12));
        assertEquals(4, TreasureRules.ceiling(0.25));
        int pity = 0;
        for (int i = 1; i < 9; i++) {
            TreasureRules.Brush b = TreasureRules.brush(pity, 0.12, 1, 0.99);
            assertFalse(b.treasure(), "brush " + i);
            pity = b.pity();
        }
        TreasureRules.Brush ninth = TreasureRules.brush(pity, 0.12, 1, 0.99);
        assertTrue(ninth.treasure());
        assertEquals(0, ninth.pity());
    }

    @Test
    void aLuckyRollAndABoostBothCount() {
        assertTrue(TreasureRules.brush(0, 0.12, 1, 0.11).treasure());
        assertFalse(TreasureRules.brush(0, 0.12, 1, 0.13).treasure());
        assertTrue(TreasureRules.brush(0, 0.12, 2, 0.23).treasure(), "the brush curio doubles the chance");
        assertEquals(1, TreasureRules.brush(0, 0.12, 2, 0.5).pity(), "but not the ceiling");
    }

    @Test
    void unfoundTreasuresAreThreeTimesAsLikely() {
        Random random = new Random(11);
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 6000; i++) {
            counts.merge(TreasureRules.pick(SHIPPED.treasures(), "desert_pyramid", Set.of("amber", "nazar"), random).id(), 1, Integer::sum);
        }
        // desert_pyramid: amber, nazar (found) and fossil_tooth (unfound): 1:1:3
        assertEquals(Set.of("amber", "nazar", "fossil_tooth"), counts.keySet());
        assertTrue(Math.abs(counts.get("fossil_tooth") - 3600) < 250, counts.toString());
        assertNull(TreasureRules.pick(SHIPPED.treasures(), "nowhere", Set.of(), random));
    }

    @Test
    void chestsRollEachTreasureOnItsOwnChance() {
        Random random = new Random(5);
        int coins = 0;
        for (int i = 0; i < 2000; i++) {
            coins += TreasureRules.chest(SHIPPED.treasures(), "chests/bastion_treasure", random).size();
        }
        assertTrue(Math.abs(coins - 600) < 80, "gold coins at 0.3: " + coins);
        assertEquals(List.of(), TreasureRules.chest(SHIPPED.treasures(), "chests/igloo_chest", random));
    }

    @Test
    void hintsSayWhereTheyTurnUp() {
        assertEquals("Brushed at desert pyramids and desert wells; in desert pyramid chests", TreasureRules.where(treasure("amber")));
        assertEquals("In bastion chests", TreasureRules.where(treasure("piglin_coin")));
    }

    @Test
    void badTreasuresAreSkipped() {
        TreasureConfigParser.Result r = TreasureConfigParser.parse(new Yaml().load("""
                sites: {well: [archaeology/desert_well]}
                list:
                  good: {kind: gem, brush: [well]}
                  no_kind: {brush: [well]}
                  unknown_site: {kind: gem, brush: [moon]}
                  nowhere: {kind: coin}
                  bad_chance: {kind: coin, chests: {chests/x: 2}}
                  chart_without_target: {kind: chart, brush: [well]}
                  gem_with_target: {kind: gem, brush: [well], chart: minecraft:shipwreck}
                """));
        assertEquals(List.of("good"), r.treasures().stream().map(TreasureDefinition::id).toList());
        assertEquals(6, r.problems().size(), r.problems().toString());
    }
}
