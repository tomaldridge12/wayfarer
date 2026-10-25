package dev.wayfarer.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The progression.yml shipped in the jar must parse cleanly and match the built-in defaults. */
class ShippedProgressionConfigTest {
    @Test
    void shippedConfigParsesWithoutProblemsToTheDefaults() throws Exception {
        Map<String, Object> root;
        try (InputStream in = getClass().getResourceAsStream("/progression.yml")) {
            root = new Yaml().load(in);
        }
        ProgressionSettings settings = ProgressionSettings.parse(root, ProgressionSettings.ANY);
        assertEquals(List.of(), settings.problems());
        assertEquals(CreditRules.defaults(), settings.credit());
        assertEquals(java.util.List.of("wither_skeleton_skull 40", "trident 12"),
                settings.pity().stream().map(p -> p.id() + " " + p.threshold()).toList());
        assertEquals(StageSnapshots.defaults(), settings.snapshots());
        List<String> announceProblems = new java.util.ArrayList<>();
        assertEquals(dev.wayfarer.core.AnnounceSettings.defaults(),
                dev.wayfarer.core.AnnounceSettings.parse(root.get("announce"), announceProblems));
        assertEquals(List.of(), announceProblems);
    }
}
