package dev.wayfarer.marks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** marks.yml: bad marks and books are skipped with a problem each; the rest load. */
class MarkConfigParserTest {
    private static MarkConfigParser.Result parse(String yaml) {
        Map<String, Object> root = new Yaml().load(yaml);
        return MarkConfigParser.parse(root, MarkFixtures.SERVER);
    }

    private static final String DUNE = "dune: {counts: [mine:sand], ranks-at: [1, 2], tool: shovel, material: [sand], ranks: [{area: 3x1}, {area: 3x3}]}";

    @Test
    void aGoodMarkLoads() {
        MarkConfigParser.Result r = parse("marks:\n  " + DUNE + "\n");
        assertEquals(List.of(), r.problems());
        assertEquals(2, r.rules().mark("dune").orElseThrow().topRank());
    }

    @Test
    void badMarksAreSkipped() {
        MarkConfigParser.Result r = parse("""
                marks:
                  delver: {counts: [mine:stone], ranks-at: [1], tool: shovel, material: [sand], ranks: [{area: 3x3}]}
                  dune: {counts: [mine:sand], ranks-at: [1, 2], tool: shovel, material: [sand], ranks: [{area: 3x3}]}
                  reaper: {counts: [harvest:*], ranks-at: [1], tool: spoon, material: [hay_block], ranks: [{area: 3x3}]}
                  angler: {counts: ["catch:*"], ranks-at: [5, 2], tool: fishing_rod, material: [string], ranks: [{}, {}]}
                  shepherd: {counts: [shear:sheep], ranks-at: [1], tool: shears, material: [nope_wool], ranks: [{radius: 3}]}
                """);
        assertTrue(r.rules().marks().isEmpty(), r.rules().marks().toString());
        assertEquals(6, r.problems().size(), r.problems().toString());
    }

    @Test
    void badAreasRadiiAndWaitsAreSkipped() {
        MarkConfigParser.Result r = parse("""
                marks:
                  dune: {counts: [mine:sand], ranks-at: [1], tool: shovel, material: [sand], ranks: [{area: 2x2}]}
                  shepherd: {counts: [shear:sheep], ranks-at: [1], tool: shears, material: [white_wool], ranks: [{radius: 40}]}
                  angler: {counts: ["catch:*"], ranks-at: [1], tool: fishing_rod, material: [string], ranks: [{bite-wait: 2}]}
                """);
        assertTrue(r.rules().marks().isEmpty());
        assertEquals(3, r.problems().size(), r.problems().toString());
    }

    @Test
    void anUnknownTagIsLeftOutWithAProblem() {
        MarkConfigParser.Result r = parse("books:\n  felling: {enchant: excellentenchants:treefeller, counts: [\"mine:#minecraft:nope\", mine:oak_log], levels-at: [1]}\n");
        assertEquals(1, r.problems().size(), r.problems().toString());
        assertEquals(java.util.Set.of("mine:oak_log"), r.rules().book("felling").orElseThrow().selector());
    }

    @Test
    void aBookWhoseEnchantIsMissingStillCountsWithANotice() {
        MarkConfigParser.Result r = parse("books:\n  vein: {enchant: other:veinminer, counts: [mine:coal_ore], levels-at: [1, 2]}\n");
        assertEquals(List.of(), r.problems());
        assertEquals(1, r.notices().size());
        assertTrue(r.rules().book("vein").isPresent());
    }

    @Test
    void aBookAndAMarkCannotShareALadder() {
        MarkConfigParser.Result r = parse("marks:\n  " + DUNE + "\nbooks:\n  dune: {enchant: excellentenchants:x, counts: [mine:sand], levels-at: [1]}\n");
        assertEquals(1, r.problems().size(), r.problems().toString());
    }
}
