package dev.wayfarer.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Distinct ladders, find: and brush:, the seams collections stand on. */
class CollectionSeamsTest {
    private final Ladders ladders = new Ladders();
    private final ProgressionEngine engine = new ProgressionEngine(ProgressionSettings.defaults(), ladders, () -> 1L);

    private static Ladder distinct(String id, Set<String> selector, long... thresholds) {
        List<Ladder.Rung> rungs = new ArrayList<>();
        for (int i = 0; i < thresholds.length; i++) {
            rungs.add(new Ladder.Rung(thresholds[i], "test:" + id + "_" + i));
        }
        return new Ladder(id, id, "entry", selector, rungs, false, true);
    }

    private static List<String> rewards(ProgressionEngine.Outcome outcome) {
        return outcome.earned().stream().map(ProgressionEngine.Earned::reward).toList();
    }

    @Test
    void aDistinctLadderCountsEntriesAboveZeroNotTheirSum() {
        ladders.set("collections", List.of(distinct("almanac", Set.of("catch:wayfarer:fish/*"), 2)));
        ProgressionData data = new ProgressionData();
        engine.record(data, "catch:wayfarer:fish/carp");
        engine.record(data, "catch:wayfarer:fish/carp");
        engine.record(data, "catch:wayfarer:fish/carp");
        engine.record(data, "catch:cod");
        assertEquals(1, ladders.find("almanac").orElseThrow().progress(data));
        assertEquals(List.of("test:almanac_0"), rewards(engine.record(data, "catch:wayfarer:fish/pike")));
    }

    @Test
    void exactAndPrefixEntriesOfOneActivityCountOnce() {
        Ladder ladder = distinct("bestiary", Set.of("kill:zombie", "kill:*"), 10);
        ProgressionData data = new ProgressionData();
        data.set("kill:zombie", 40);
        data.set("kill:husk", 2);
        data.set("kill:creeper", 0);
        assertEquals(2, ladder.progress(data));
    }

    @Test
    void tagSelectorsExpandAndBadEntriesAreReported() {
        List<String> problems = new ArrayList<>();
        Map<String, Set<String>> tags = Map.of("minecraft:decorated_pot_sherds", Set.of("angler_pottery_sherd", "arms_up_pottery_sherd"));
        Set<String> selector = Selectors.parse(List.of("find:#minecraft:decorated_pot_sherds", "find:#minecraft:nope", "kill:*",
                "nonsense"), t -> tags.getOrDefault(t, Set.of()), problems::add, "Cabinet");
        assertEquals(Set.of("find:angler_pottery_sherd", "find:arms_up_pottery_sherd", "kill:*"), selector);
        assertEquals(2, problems.size());
        ladders.set("collections", List.of(distinct("cabinet", selector, 2)));
        ProgressionData data = new ProgressionData();
        engine.find(data, List.of("angler_pottery_sherd"));
        assertEquals(List.of("test:cabinet_0"), rewards(engine.find(data, List.of("arms_up_pottery_sherd"))));
    }

    @Test
    void findCountsEachItemOnceAndStaysAtOne() {
        ProgressionData data = new ProgressionData();
        ProgressionEngine.Outcome first = engine.find(data, List.of("amethyst_shard", "wayfarer:treasure/amber", "amethyst_shard"));
        assertEquals(List.of("find:amethyst_shard", "find:wayfarer:treasure/amber"), first.raised());
        assertFalse(engine.find(data, List.of("amethyst_shard")).counted());
        assertEquals(1, data.count("find:amethyst_shard"));
        assertEquals(List.of("find:dirt"), engine.find(data, List.of("amethyst_shard", "dirt")).raised());
    }

    @Test
    void brushCountsEveryFirstBrush() {
        ProgressionData data = new ProgressionData();
        assertTrue(engine.brush(data, "suspicious_sand").counted());
        engine.brush(data, "suspicious_sand");
        assertEquals(2, data.count("brush:suspicious_sand"));
    }

    @Test
    void aGroupCountsOnceInADistinctLadderAndIsReadByEachAlternative() {
        String villages = "discover:structure/village_plains|discover:structure/village_desert";
        ladders.set("collections", List.of(distinct("atlas", Set.of(villages, "discover:structure/igloo"), 2)));
        ProgressionData data = new ProgressionData();
        engine.discoverStructure(data, "village_plains", true);
        engine.discoverStructure(data, "village_desert", true);
        assertEquals(1, ladders.find("atlas").orElseThrow().progress(data));
        assertEquals(List.of("test:atlas_0"), rewards(engine.discoverStructure(data, "igloo", true)));
        assertTrue(ladders.find("atlas").orElseThrow().reads("discover:structure/village_desert"));
        assertEquals(1, ladders.reading("discover:structure/village_plains").size());
    }

    @Test
    void aLadderAddedLaterIsCreditedFromPastPlayOnRecheck() {
        ProgressionData data = new ProgressionData();
        data.set("mine:moss_block", 12_000);
        assertFalse(engine.recheck(data).counted());
        ladders.set("objectives", List.of(new Ladder("feat_moss", "Moss Tender", "block", Set.of("mine:moss_block"),
                List.of(new Ladder.Rung(10_000, "curio:moss_heart")), true)));
        assertEquals(List.of("curio:moss_heart"), rewards(engine.recheck(data)));
        assertFalse(engine.recheck(data).counted(), "awarded once");
    }
}
