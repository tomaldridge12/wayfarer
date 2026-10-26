package dev.wayfarer.comforts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** How comforts.yml reads: the shipped defaults, and the fallback for every bad value. */
class ComfortsTest {
    private static Map<String, Object> shipped() {
        try (InputStream in = ComfortsTest.class.getResourceAsStream("/comforts.yml")) {
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void theShippedFileReadsWithNoProblems() {
        ComfortsConfigParser.Result r = ComfortsConfigParser.parse(shipped());
        assertEquals(List.of(), r.problems());
        assertEquals(new ComfortsConfigParser.Settings(true, 4, 1, true, true, true, true), r.settings());
    }

    @Test
    void anEmptyFileGivesTheDefaultsWithoutProblems() {
        ComfortsConfigParser.Result r = ComfortsConfigParser.parse(Map.of());
        assertTrue(r.problems().isEmpty());
        assertEquals(new ComfortsConfigParser.Settings(true, 4, 1, true, true, true, true), r.settings());
    }

    @Test
    void theRatioAndSwitchesAreRead() {
        ComfortsConfigParser.Result r = ComfortsConfigParser.parse(Map.of(
                "leather-from-flesh", Map.of("enabled", false, "flesh", 6, "leather", 2),
                "keep-on-death", Map.of("enabled", true, "hotbar", false, "armour", true, "offhand", false)));
        assertTrue(r.problems().isEmpty());
        assertEquals(new ComfortsConfigParser.Settings(false, 6, 2, true, false, true, false), r.settings());
    }

    @Test
    void aRatioOutsideTheGridFallsBackWithAProblemLine() {
        for (Object bad : new Object[] {0, 10, -3, 2.5, "four"}) {
            ComfortsConfigParser.Result r = ComfortsConfigParser.parse(Map.of("leather-from-flesh", Map.of("flesh", bad)));
            assertEquals(4, r.settings().flesh(), "flesh " + bad);
            assertEquals(1, r.problems().size(), "flesh " + bad);
            assertTrue(r.problems().getFirst().startsWith("leather-from-flesh.flesh"));
        }
        assertEquals(9, ComfortsConfigParser.parse(Map.of("leather-from-flesh", Map.of("flesh", 9))).settings().flesh());
        ComfortsConfigParser.Result r = ComfortsConfigParser.parse(Map.of("leather-from-flesh", Map.of("leather", 65)));
        assertEquals(1, r.settings().leather());
        assertFalse(r.problems().isEmpty());
    }

    @Test
    void aSwitchThatIsNotTrueOrFalseFallsBackToOn() {
        ComfortsConfigParser.Result r = ComfortsConfigParser.parse(Map.of("keep-on-death", Map.of("hotbar", "maybe")));
        assertTrue(r.settings().hotbar());
        assertEquals(1, r.problems().size());
        assertTrue(r.problems().getFirst().startsWith("keep-on-death.hotbar"));
    }
}
