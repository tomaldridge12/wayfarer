package dev.wayfarer.enchanting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The reshuffle: when a click triggers it, who can pay, what it costs and how the config reads. */
class EnchantingTest {
    private static final Set<String> ITEMS = Set.of("lapis_lazuli", "diamond", "emerald");

    private static Map<String, Object> shipped() {
        try (InputStream in = EnchantingTest.class.getResourceAsStream("/enchanting.yml")) {
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void onlySneakingWithTheTriggerOnATableTriggers() {
        assertTrue(EnchantingRules.triggers(true, true, "lapis_lazuli", "lapis_lazuli"));
        assertFalse(EnchantingRules.triggers(true, false, "lapis_lazuli", "lapis_lazuli"), "plain click opens the table as normal");
        assertFalse(EnchantingRules.triggers(true, true, null, "lapis_lazuli"), "an empty hand opens the table as normal");
        assertFalse(EnchantingRules.triggers(true, true, "diamond", "lapis_lazuli"));
        assertFalse(EnchantingRules.triggers(false, true, "lapis_lazuli", "lapis_lazuli"), "not a main-hand right-click on a table");
    }

    @Test
    void aReshuffleNeedsALevelAndIsFreeInCreative() {
        assertEquals(EnchantingRules.Verdict.RESHUFFLE, EnchantingRules.verdict(true, 1, false, 1));
        assertEquals(EnchantingRules.Verdict.RESHUFFLE, EnchantingRules.verdict(true, 30, false, 1));
        assertEquals(EnchantingRules.Verdict.NEEDS_LEVEL, EnchantingRules.verdict(true, 0, false, 1));
        assertEquals(EnchantingRules.Verdict.RESHUFFLE, EnchantingRules.verdict(true, 0, true, 1));
        assertEquals(EnchantingRules.Verdict.IGNORE, EnchantingRules.verdict(false, 30, false, 1));
        assertEquals(EnchantingRules.Verdict.IGNORE, EnchantingRules.verdict(false, 0, false, 1));
        assertEquals(4, EnchantingRules.levelAfter(5, 1, false));
        assertEquals(5, EnchantingRules.levelAfter(5, 1, true), "creative pays nothing");
    }

    @Test
    void theShippedConfigLoadsWithNoProblems() {
        EnchantingConfigParser.Result r = EnchantingConfigParser.parse(shipped(), ITEMS::contains);
        assertEquals(List.of(), r.problems());
        assertTrue(r.settings().reshuffle());
        assertEquals(1, r.settings().levels());
        assertEquals("lapis_lazuli", r.settings().trigger());
        // Vanilla's cheapest enchant costs one level (plus a lapis); a reshuffle must never take more than that (ADR 0007).
        assertEquals(1, r.settings().levels());
    }

    @Test
    void badValuesGiveAProblemAndTheDefault() {
        Map<String, Object> bad = Map.of("reshuffle", Map.of("enabled", "yes", "levels", 0, "trigger", "dirtt"));
        EnchantingConfigParser.Result r = EnchantingConfigParser.parse(bad, ITEMS::contains);
        assertEquals(3, r.problems().size());
        assertTrue(r.settings().reshuffle());
        assertEquals(1, r.settings().levels());
        assertEquals("lapis_lazuli", r.settings().trigger());
    }

    @Test
    void anotherTriggerItemAndSwitchedOffReadThrough() {
        Map<String, Object> cfg = Map.of("reshuffle", Map.of("enabled", false, "levels", 2, "trigger", "minecraft:Diamond"));
        EnchantingConfigParser.Result r = EnchantingConfigParser.parse(cfg, ITEMS::contains);
        assertEquals(List.of(), r.problems());
        assertFalse(r.settings().reshuffle());
        assertEquals(2, r.settings().levels());
        assertEquals("diamond", r.settings().trigger());
    }
}
