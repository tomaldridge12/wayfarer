package dev.wayfarer.marks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wayfarer.core.progression.Ladder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The ladders marks register, the smithing decision, areas and rank numbers. */
class MarkRulesTest {
    private final MarkRules rules = MarkFixtures.rules();

    @Test
    void shippedConfigLoadsFourMarksAndTwoBooksWithoutProblems() {
        MarkConfigParser.Result r = MarkFixtures.shipped();
        assertEquals(List.of(), r.problems());
        assertEquals(List.of(), r.notices());
        assertEquals(List.of("dune", "shepherd", "angler", "reaper"), rules.marks().stream().map(MarkDefinition::id).toList());
        assertEquals(List.of("veinminer", "treefeller"), rules.books().stream().map(BookDefinition::id).toList());
    }

    @Test
    void eachMarkLadderPaysItsRanksAndOnlyTheTopRankAnnounces() {
        Ladder dune = rules.ladders().getFirst();
        assertEquals("Dune Mark", dune.title());
        assertEquals(Set.of("mine:sand", "mine:red_sand"), dune.selector());
        assertEquals(List.of(500L, 2500L, 8000L), dune.rungs().stream().map(Ladder.Rung::threshold).toList());
        assertEquals(List.of("mark:dune_1", "mark:dune_2", "mark:dune_3"), dune.rungs().stream().map(Ladder.Rung::reward).toList());
        assertFalse(dune.announces("mark:dune_1"));
        assertFalse(dune.announces("mark:dune_2"));
        assertTrue(dune.announces("mark:dune_3"));
    }

    @Test
    void tagsExpandAndPrefixesStay() {
        Map<String, Ladder> byId = new java.util.HashMap<>();
        rules.ladders().forEach(l -> byId.put(l.id(), l));
        assertEquals(Set.of("catch:*"), byId.get("angler").selector());
        assertEquals(Set.of("mine:oak_log", "mine:spruce_log", "mine:stripped_oak_log"), byId.get("treefeller").selector());
        assertTrue(byId.get("veinminer").selector().containsAll(Set.of("mine:coal_ore", "mine:deepslate_coal_ore",
                "mine:nether_quartz_ore")));
        assertEquals(List.of("book:veinminer_1", "book:veinminer_2", "book:veinminer_3"),
                byId.get("veinminer").rungs().stream().map(Ladder.Rung::reward).toList());
        assertEquals(Set.of("white_wool", "black_wool"), rules.mark("shepherd").orElseThrow().materials());
    }

    @Test
    void booksAreRegisteredRewardEnchantsAtTheirTopLevel() {
        assertEquals(Map.of("excellentenchants:veinminer", 3, "excellentenchants:treefeller", 3), rules.rewardEnchants());
    }

    @Test
    void rewardIdsNameARankThisConfigHas() {
        assertEquals(Optional.of(new MarkRules.OnTool("dune", 2)), rules.rewardId("dune_2"));
        assertEquals(Optional.empty(), rules.rewardId("dune_4"));
        assertEquals(Optional.empty(), rules.rewardId("delver_1"));
        assertEquals(Optional.of(new MarkRules.OnTool("veinminer", 3)), rules.bookId("veinminer_3"));
        assertEquals(Optional.empty(), rules.bookId("veinminer_0"));
    }

    // --- smithing ---

    private static MarkRules.OnTool on(String mark, int rank) {
        return new MarkRules.OnTool(mark, rank);
    }

    @Test
    void aTemplateMarksTheRightToolWithTheRightMaterial() {
        assertEquals(Optional.of(on("dune", 1)), rules.smith(on("dune", 1), "iron_shovel", null, "sand"));
        assertEquals(Optional.of(on("dune", 2)), rules.smith(on("dune", 2), "netherite_shovel", null, "red_sand"));
        assertEquals(Optional.of(on("shepherd", 3)), rules.smith(on("shepherd", 3), "shears", null, "black_wool"));
        assertEquals(Optional.of(on("angler", 1)), rules.smith(on("angler", 1), "fishing_rod", null, "string"));
        assertEquals(Optional.of(on("reaper", 1)), rules.smith(on("reaper", 1), "diamond_hoe", null, "hay_block"));
    }

    @Test
    void wrongToolsAndMaterialsAreRefused() {
        assertEquals(Optional.empty(), rules.smith(on("dune", 1), "iron_pickaxe", null, "sand"));
        assertEquals(Optional.empty(), rules.smith(on("dune", 1), "iron_shovel", null, "gravel"));
        assertEquals(Optional.empty(), rules.smith(on("reaper", 1), "iron_shovel", null, "hay_block"));
        assertEquals(Optional.empty(), rules.smith(on("dune", 4), "iron_shovel", null, "sand"));
        assertEquals(Optional.empty(), rules.smith(on("delver", 1), "iron_pickaxe", null, "stone"));
    }

    @Test
    void aHigherRankReplacesALowerOneButNeverTheOtherWay() {
        assertEquals(Optional.of(on("dune", 3)), rules.smith(on("dune", 3), "iron_shovel", on("dune", 1), "sand"));
        assertEquals(Optional.empty(), rules.smith(on("dune", 2), "iron_shovel", on("dune", 2), "sand"));
        assertEquals(Optional.empty(), rules.smith(on("dune", 1), "iron_shovel", on("dune", 3), "sand"));
    }

    // --- areas ---

    @Test
    void duneOneDigsARowAcrossTheFace() {
        // Digging the north face of a wall: the row runs along x.
        assertEquals(Set.of(new Offset(-1, 0, 0), new Offset(1, 0, 0)),
                Set.copyOf(rules.dune(1, Face.NORTH, Face.SOUTH, o -> "sand")));
        // Digging down while facing east: the row runs across the facing, along z.
        assertEquals(Set.of(new Offset(0, 0, -1), new Offset(0, 0, 1)),
                Set.copyOf(rules.dune(1, Face.UP, Face.EAST, o -> "sand")));
    }

    @Test
    void duneTwoDigsASquareOnTheFace() {
        List<Offset> square = rules.dune(2, Face.EAST, Face.WEST, o -> "red_sand");
        assertEquals(8, square.size());
        assertTrue(square.stream().allMatch(o -> o.dx() == 0));
        assertEquals(8, rules.dune(2, Face.DOWN, Face.NORTH, o -> "sand").stream().filter(o -> o.dy() == 0).count());
    }

    @Test
    void duneDigsOnlySandAndGravelOnlyAtRankThree() {
        java.util.function.Function<Offset, String> mixed = o -> o.dx() < 0 ? "gravel" : o.dx() > 0 ? "suspicious_sand" : "sand";
        assertTrue(rules.dune(2, Face.NORTH, Face.NORTH, mixed).stream().allMatch(o -> o.dx() == 0));
        List<Offset> three = rules.dune(3, Face.NORTH, Face.NORTH, mixed);
        assertTrue(three.stream().anyMatch(o -> o.dx() < 0));
        assertTrue(three.stream().noneMatch(o -> o.dx() > 0), "never suspicious sand");
        assertFalse(rules.duneDigs(3, "suspicious_gravel"));
        assertFalse(rules.duneDigs(2, "gravel"));
        assertTrue(rules.duneDigs(3, "gravel"));
        assertFalse(rules.duneDigs(3, "stone"));
    }

    @Test
    void reaperAreasGrowWithRank() {
        assertEquals(Set.of(new Offset(-1, 0, 0), new Offset(1, 0, 0)), Set.copyOf(rules.reaper(1, Face.NORTH)));
        assertEquals(8, rules.reaper(2, Face.NORTH).size());
        assertEquals(24, rules.reaper(3, Face.EAST).size());
        assertTrue(rules.reaper(3, Face.EAST).stream().allMatch(o -> o.dy() == 0));
    }

    @Test
    void shepherdAndAnglerNumbersPerRank() {
        MarkDefinition shepherd = rules.mark("shepherd").orElseThrow();
        assertEquals(List.of(3, 5, 7), shepherd.ranks().stream().map(MarkDefinition.Rank::radius).toList());
        assertEquals(0.25, shepherd.rank(3).chance());
        assertEquals(0, shepherd.rank(2).chance());
        MarkDefinition angler = rules.mark("angler").orElseThrow();
        assertEquals(List.of(0.85, 0.70, 0.55), angler.ranks().stream().map(MarkDefinition.Rank::biteWait).toList());
        assertEquals(List.of(0.0, 0.05, 0.10), angler.ranks().stream().map(MarkDefinition.Rank::chance).toList());
    }
}
