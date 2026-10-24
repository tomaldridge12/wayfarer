package dev.wayfarer.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** curios.yml: every shipped curio loads; a bad curio is skipped with one problem and the rest still load. */
class CurioConfigParserTest {
    private static CurioConfigParser.Result parse(String yaml) {
        Map<String, Object> root = new Yaml().load(yaml);
        return CurioConfigParser.parse(root.get("curios"), CurioFixtures.SERVER);
    }

    @Test
    void theShippedCuriosAllLoadWithAHandWrittenLineEach() {
        CurioConfigParser.Result r = CurioConfigParser.parse(CurioFixtures.shippedYaml().get("curios"), CurioFixtures.SERVER);
        assertEquals(List.of(), r.problems());
        assertTrue(r.curios().size() >= 30, "curios: " + r.curios().size());
        r.curios().values().forEach(c -> {
            assertFalse(c.flavour().isBlank(), c.id() + " has no flavour line");
            assertTrue(c.description().stream().allMatch(l -> l.length() <= 36), c.id() + " has a description line over 36 characters");
        });
        assertInstanceOf(Effect.Spread.class, r.curios().get("moss_heart").effect());
        assertEquals(120, r.curios().get("moss_heart").cooldownSeconds());
    }

    @Test
    void badCuriosAreSkippedWithAProblemEach() {
        CurioConfigParser.Result r = parse("""
                curios:
                  good: {effect: potion, potion: speed, description: "Fast."}
                  unknown_effect: {effect: teleport, description: "x"}
                  bad_potion: {effect: potion, potion: nope_juice, description: "x"}
                  tool_without_cooldown: {effect: light, seconds: 10, description: "x"}
                  bad_when: {effect: potion, potion: speed, when: "on tuesdays", description: "x"}
                  silent: {effect: potion, potion: speed}
                  bad_colour: {effect: potion, potion: speed, colour: green, description: "x"}
                  tool_with_when: {effect: light, cooldown: 5, when: night, description: "x"}
                  empty_spread: {effect: spread, cooldown: 5, description: "x"}
                """);
        assertEquals(List.of("good"), List.copyOf(r.curios().keySet()));
        assertEquals(8, r.problems().size(), r.problems().toString());
    }

    @Test
    void conditionsParseAndUnknownOnesDoNot() {
        assertEquals("below", Condition.parse("below -10").orElseThrow().kind());
        assertEquals("minecraft:is_ocean", Condition.parse("biome #minecraft:is_ocean").orElseThrow().argument());
        assertEquals("fishing_rod", Condition.parse("holding fishing_rod").orElseThrow().argument());
        assertTrue(Condition.parse("holding banana").isEmpty());
        assertTrue(Condition.parse("biome minecraft:is_ocean").isEmpty());
        assertTrue(Condition.parse("below sea").isEmpty());
    }
}
