package dev.wayfarer.objectives;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** Weekend events: the shipped four, the rotation turning one step a week, multipliers and bad events. */
class EventTest {
    private static final PoolConfigParser.Result POOLS = PoolConfigParser.parse(ObjectiveFixtures.shippedYaml(), ObjectiveFixtures.SERVER,
            key -> true);
    private static final EventConfigParser.Result SHIPPED = EventConfigParser.parse(ObjectiveFixtures.shippedYaml().get("events"),
            ObjectiveFixtures.SERVER, POOLS.tables());
    private static final ZoneId LONDON = ZoneId.of("Europe/London");

    @Test
    void theShippedEventsLoadInRotation() {
        assertEquals(List.of(), SHIPPED.problems());
        assertEquals(List.of("drowned_tide", "harvest_moon", "deep_rush", "bone_night", "harvest_feast"), SHIPPED.rotation());
        assertEquals(12, SHIPPED.events().get("harvest_moon").number("tick-speed", 0));
        assertEquals("forage_chance", SHIPPED.events().get("harvest_feast").modifier());
        assertEquals(2, SHIPPED.events().get("harvest_feast").number("multiplier", 0));
        assertEquals("Eat 300 dishes between you", SHIPPED.events().get("harvest_feast").goal().task());
        assertEquals("Catch 400 fish between you", SHIPPED.events().get("drowned_tide").goal().task());
    }

    @Test
    void theRotationTurnsOneStepAWeekAndRepeats() {
        Instant friday = Instant.parse("2026-10-23T17:00:00Z");
        String first = EventRules.forWeekend(SHIPPED.rotation(), friday, LONDON);
        String second = EventRules.forWeekend(SHIPPED.rotation(), friday.plus(Duration.ofDays(7)), LONDON);
        assertNotEquals(first, second);
        assertEquals(first, EventRules.forWeekend(SHIPPED.rotation(), friday.plus(Duration.ofDays(35)), LONDON));
        assertEquals(first, EventRules.forWeekend(SHIPPED.rotation(), friday.plus(Duration.ofHours(30)), LONDON), "same weekend");
        assertEquals(null, EventRules.forWeekend(List.of(), friday, LONDON));
    }

    @Test
    void multipliersRound() {
        assertEquals(10, EventRules.multiply(5, 2));
        assertEquals(8, EventRules.multiply(5, 1.5));
    }

    @Test
    void badEventsAreSkipped() {
        EventConfigParser.Result r = EventConfigParser.parse(new Yaml().load("""
                rotation: [good, ghost]
                list:
                  good: {modifier: ore_xp, goal: {text: "Mine {n}", counts: [mine:stone], target: 5}}
                  no_goal: {modifier: ore_xp}
                  bad_modifier: {modifier: moon_gravity, goal: {text: "x", counts: [mine:stone], target: 5}}
                  drops_without_mobs: {modifier: mob_drops, goal: {text: "x", counts: [mine:stone], target: 5}}
                """), ObjectiveFixtures.SERVER, POOLS.tables());
        assertEquals(Set.of("good"), r.events().keySet());
        assertEquals(List.of("good"), r.rotation());
        assertEquals(4, r.problems().size(), r.problems().toString());
    }
}
