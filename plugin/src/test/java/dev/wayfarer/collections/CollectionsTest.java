package dev.wayfarer.collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wayfarer.core.progression.Ladder;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** collections.yml, milestone thresholds, the distinct ladder, found counts and the Journal's small helpers. */
class CollectionsTest {
    static Map<String, Object> shipped() {
        try (InputStream in = CollectionsTest.class.getResourceAsStream("/collections.yml")) {
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static CollectionEntry entry(String key, String activity) {
        return new CollectionEntry(key, activity, "#" + key, "stone", "hint", "", "found");
    }

    @Test
    void theShippedCollectionsLoadWithoutProblems() {
        CollectionConfigParser.Result r = CollectionConfigParser.parse(shipped());
        assertEquals(List.of(), r.problems());
        assertTrue(r.collections().keySet().containsAll(List.of("bestiary", "atlas", "cabinet", "curios")));
        assertEquals("excellentenchants:decapitator_1".length() + 8,
                r.collections().get("bestiary").milestones().getLast().rewards().getFirst().length());
        assertTrue(r.settings().bookOnFirstJoin());
        assertTrue(((Map<?, ?>) r.collections().get("atlas").settings().get("groups")).containsKey("village"));
    }

    @Test
    void aRewardPaidTwiceAndBadMilestonesAreDropped() {
        CollectionConfigParser.Result r = CollectionConfigParser.parse(new Yaml().load("""
                collections:
                  one: {milestones: {"10": [curio:bone_dice], "nope": [curio:x], "150%": [curio:y], "5": [plain]}}
                  two: {milestones: {"10": [curio:bone_dice, curio:glow_jar]}}
                  three: {colour: red}
                """));
        assertEquals(4 + 1, r.problems().size(), r.problems().toString());
        assertEquals(List.of("curio:glow_jar"), r.collections().get("two").milestones().getFirst().rewards());
        assertEquals(2, r.collections().size());
    }

    @Test
    void sharesRoundUpAndCountsAreCappedAtTheSize() {
        assertEquals(20, CollectionRules.threshold(new CollectionDefinition.Milestone("25%", List.of()), 80));
        assertEquals(21, CollectionRules.threshold(new CollectionDefinition.Milestone("25%", List.of()), 81));
        assertEquals(81, CollectionRules.threshold(new CollectionDefinition.Milestone("100%", List.of()), 81));
        assertEquals(5, CollectionRules.threshold(new CollectionDefinition.Milestone("10", List.of()), 5));
        assertEquals(1, CollectionRules.threshold(new CollectionDefinition.Milestone("1%", List.of()), 5));
    }

    @Test
    void theLadderIsDistinctPaysEachRewardAndAnnouncesOnlyCompletion() {
        CollectionDefinition def = new CollectionDefinition("atlas", "Atlas", 0, "", "place",
                List.of(new CollectionDefinition.Milestone("1", List.of("curio:owl_feather", "supply:seed_bundle")),
                        new CollectionDefinition.Milestone("100%", List.of("gear:featherstep_boots"))), Map.of(), Map.of());
        List<CollectionEntry> entries = List.of(entry("plains", "discover:biome/plains"),
                entry("village", "discover:structure/village_plains|discover:structure/village_desert"));
        Ladder ladder = CollectionRules.ladder(def, entries);
        assertTrue(ladder.distinct());
        assertEquals("collection_atlas", ladder.id());
        assertEquals(3, ladder.rungs().size());
        assertTrue(!ladder.announces("curio:owl_feather") && ladder.announces("gear:featherstep_boots"));
        Map<String, Long> counters = Map.of("discover:structure/village_desert", 2L);
        assertEquals(2, CollectionRules.count(entries.get(1), counters));
        assertEquals(1, CollectionRules.found(entries, counters));
    }

    @Test
    void barsTitlesAndUnits() {
        assertEquals("▰▰▱▱", CollectionRules.bar(1, 2, 4));
        assertEquals("▱▱▱▱", CollectionRules.bar(0, 0, 4));
        assertEquals("Alpine Grove", CollectionRules.title("terralith:alpine_grove"));
        assertEquals("Village Plains", CollectionRules.title("structure/village_plains"));
        CollectionDefinition fish = new CollectionDefinition("almanac", "Almanac", 0, "", "fish|fish", List.of(), Map.of(), Map.of());
        assertEquals("fish", fish.unit(3));
        CollectionDefinition curio = new CollectionDefinition("curios", "Curios", 0, "", "curio", List.of(), Map.of(), Map.of());
        assertEquals("curios", curio.unit(2));
    }

    @Test
    void datapackBiomesGetAFittingIconAndHint() {
        assertEquals("spruce_sapling", Icons.biome("terralith:siberian_taiga"));
        assertEquals("packed_ice", Icons.biome("terralith:glacial_chasm"));
        assertEquals("grass_block", Icons.biome("terralith:something_new"));
        assertEquals("Somewhere you have not stood yet.", Icons.biomeHint("terralith:something_new"));
        assertEquals("bell", Icons.structure("village"));
    }

    @Test
    void enchantRewardsRegisterTheirHighestLevel() {
        Map<String, Integer> into = new java.util.LinkedHashMap<>();
        dev.wayfarer.core.RewardEnchants.addReward("enchant:excellentenchants:decapitator_1", into);
        dev.wayfarer.core.RewardEnchants.addReward("enchant:excellentenchants:decapitator_3", into);
        dev.wayfarer.core.RewardEnchants.addReward("enchant:broken", into);
        assertEquals(Map.of("excellentenchants:decapitator", 3), into);
    }
}
