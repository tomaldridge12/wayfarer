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
        ProgressionSettings settings = ProgressionSettings.parse(root, name -> true);
        assertEquals(List.of(), settings.problems());
        assertEquals(CreditRules.defaults(), settings.credit());
    }
}
