package dev.wayfarer.objectives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Windows (06:00 London, Monday, the weekend), draws by stage, progress as a delta, rerolls, streaks, tables, tokens. */
class ObjectiveRulesTest {
    private static final PoolConfigParser.Result SHIPPED = PoolConfigParser.parse(ObjectiveFixtures.shippedYaml(), ObjectiveFixtures.SERVER,
            key -> !key.startsWith("nope"));
    private static final Schedule LONDON = Schedule.defaults();

    private static Instant london(String local) {
        return ZonedDateTime.parse(local + "[Europe/London]").toInstant();
    }

    @Test
    void theShippedPoolsAndTablesLoad() {
        assertEquals(List.of(), SHIPPED.problems());
        assertEquals(66, SHIPPED.daily().size());
        assertEquals(14, SHIPPED.weekly().size());
        assertEquals(List.of("start", "iron", "nether", "end"), SHIPPED.settings().stages());
        assertEquals("Mine 32 coal ore", SHIPPED.daily().get("coal_run").task());
        assertTrue(SHIPPED.tables().has("fishing", "goal"));
    }

    @Test
    void dailiesTurnOverAtSixLondonTimeInSummerAndWinter() {
        assertEquals(london("2026-10-24T06:00+01:00"), LONDON.dailyStart(london("2026-10-24T18:30+01:00")));
        assertEquals(london("2026-10-23T06:00+01:00"), LONDON.dailyStart(london("2026-10-24T05:59+01:00")));
        assertEquals(london("2026-12-01T06:00Z"), LONDON.dailyStart(london("2026-12-01T09:00Z")), "GMT in winter");
        // The clocks go back on 25 October 2026: that day's window opens at 06:00 GMT.
        assertEquals(london("2026-10-25T06:00Z"), LONDON.nextDaily(london("2026-10-24T20:00+01:00")));
        assertEquals(LONDON.day(london("2026-10-24T07:00+01:00")) + 1, LONDON.day(london("2026-10-25T07:00Z")));
    }

    @Test
    void theWeeklyTurnsOverOnMondayAndEventsRunTheWeekend() {
        assertEquals(london("2026-10-19T06:00+01:00"), LONDON.weeklyStart(london("2026-10-24T12:00+01:00")));
        assertEquals(london("2026-10-12T06:00+01:00"), LONDON.weeklyStart(london("2026-10-19T05:00+01:00")));
        assertTrue(LONDON.event(london("2026-10-23T17:59+01:00")).isEmpty(), "Friday before 18:00");
        Instant[] weekend = LONDON.event(london("2026-10-23T18:00+01:00")).orElseThrow();
        assertEquals(london("2026-10-26T00:00Z"), weekend[1], "ends Sunday midnight (the clocks went back)");
        assertTrue(LONDON.event(london("2026-10-25T23:59Z")).isPresent());
        assertTrue(LONDON.event(london("2026-10-26T00:00Z")).isEmpty());
        assertTrue(LONDON.event(london("2026-10-21T12:00+01:00")).isEmpty(), "midweek");
    }

    @Test
    void drawsNeverGoAboveTheStageNeverRepeatAndVaryTheThemes() {
        Random random = new Random(1);
        for (int i = 0; i < 200; i++) {
            List<ObjectiveDefinition> drawn = ObjectiveRules.draw(SHIPPED.daily().values(), SHIPPED.settings().stages(), "start", 3,
                    Set.of(), random);
            assertEquals(3, drawn.size());
            assertTrue(drawn.stream().allMatch(d -> d.stage().equals("start")), drawn.toString());
            assertEquals(3, drawn.stream().map(ObjectiveDefinition::id).distinct().count());
            assertEquals(3, drawn.stream().map(ObjectiveDefinition::table).distinct().count(), "a varied day");
        }
        List<ObjectiveDefinition> excluded = ObjectiveRules.draw(SHIPPED.daily().values(), SHIPPED.settings().stages(), "end", 66,
                Set.of("coal_run"), random);
        assertEquals(65, excluded.size());
        assertFalse(excluded.stream().anyMatch(d -> d.id().equals("coal_run")));
    }

    @Test
    void progressIsWhatWasDoneSinceTheDraw() {
        ObjectiveDefinition coal = SHIPPED.daily().get("coal_run");
        Map<String, Long> then = Map.of("mine:coal_ore", 500L);
        long baseline = ObjectiveRules.value(coal, then);
        assertEquals(0, ObjectiveRules.progress(coal, then, baseline), "nothing before the draw counts");
        assertEquals(12, ObjectiveRules.progress(coal, Map.of("mine:coal_ore", 510L, "mine:deepslate_coal_ore", 2L), baseline));
        ObjectiveDefinition species = SHIPPED.daily().get("something_new");
        long had = ObjectiveRules.value(species, Map.of("catch:wayfarer:fish/carp", 9L));
        assertEquals(1, ObjectiveRules.progress(species, Map.of("catch:wayfarer:fish/carp", 12L, "catch:wayfarer:fish/pike", 1L), had),
                "more carp is not a new species");
    }

    @Test
    void rerollsCostMoreEachTimeAndStreaksPauseRatherThanReset() {
        assertEquals(3, ObjectiveRules.rerollCost(3, 2, 0));
        assertEquals(7, ObjectiveRules.rerollCost(3, 2, 2));
        assertEquals(new ObjectiveRules.Streak(1, false), ObjectiveRules.completeDay(0, Long.MIN_VALUE, 100, 7));
        assertEquals(new ObjectiveRules.Streak(6, false), ObjectiveRules.completeDay(5, 90, 100, 7), "a gap only pauses");
        assertEquals(new ObjectiveRules.Streak(7, true), ObjectiveRules.completeDay(6, 99, 100, 7));
        assertEquals(new ObjectiveRules.Streak(7, false), ObjectiveRules.completeDay(7, 100, 100, 7), "counted once a day");
    }

    @Test
    void tablesRollByWeightTurnOwnedUniquesIntoTokensAndPickLevels() {
        RewardTables tables = new RewardTables(Map.of("t", Map.of("daily", List.of(
                new RewardTables.Entry("curio:bone_dice", 1, 1, 1),
                new RewardTables.Entry("enchant:excellentenchants:haste", 1, 1, 3),
                new RewardTables.Entry("supply:seed_bundle", 1, 2, 2),
                new RewardTables.Entry("tokens", 1, 5, 5)))), 30);
        Map<String, Integer> seen = new HashMap<>();
        Random random = new Random(2);
        for (int i = 0; i < 4000; i++) {
            String key = tables.roll("t", "daily", r -> r.equals("curio:bone_dice"), random).getFirst();
            seen.merge(key.replaceAll("_\\d$", "_N"), 1, Integer::sum);
        }
        assertEquals(Set.of("tokens:30", "enchant:excellentenchants:haste_N", "supply:seed_bundle*2", "tokens:5"), seen.keySet());
        assertTrue(Math.abs(seen.get("tokens:30") - 1000) < 120, seen.toString());
        assertEquals(List.of(), tables.roll("t", "weekly", r -> false, random));
        assertEquals(Map.of("excellentenchants:haste", 3), tables.enchants());
        assertEquals(new RewardTables.Loot("supply:seed_bundle", 2), RewardTables.Loot.of("supply:seed_bundle*2"));
    }

    @Test
    void tokensNeverGoBelowZeroAndEveryChangeIsLedgered() {
        assertEquals(new Tokens.Change(30, 20), Tokens.add(10, 20));
        assertEquals(new Tokens.Change(10, 0), Tokens.add(10, -20));
        assertEquals("2026-10-24T17:00:00Z +20 daily coal_run (balance 30)",
                Tokens.ledger(Instant.parse("2026-10-24T17:00:00Z"), 20, "daily coal_run", 30));
    }

    @Test
    void badPoolEntriesAndTableLinesAreSkipped() {
        PoolConfigParser.Result r = PoolConfigParser.parse(new Yaml().load("""
                schedule: {zone: Mars/Olympus, daily-reset: "25:00"}
                tables:
                  t:
                    daily: [{reward: tokens, amount: 5}, {reward: "item:nope", weight: 1}, {reward: banana}]
                    yearly: []
                daily-pool:
                  good: {text: "Mine {n}", counts: [mine:stone], target: 5, table: t}
                  no_table: {text: "x", counts: [mine:stone], target: 5, table: missing}
                  no_text: {counts: [mine:stone], target: 5, table: t}
                  bad_stage: {text: "x", counts: [mine:stone], target: 5, table: t, stage: moon}
                """), ObjectiveFixtures.SERVER, key -> !key.startsWith("nope"));
        assertEquals(Set.of("good"), r.daily().keySet());
        assertEquals(8, r.problems().size(), r.problems().toString());
    }
}
