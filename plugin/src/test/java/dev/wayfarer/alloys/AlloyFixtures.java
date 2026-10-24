package dev.wayfarer.alloys;

import java.io.InputStream;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/** The shipped alloys.yml, parsed, for tests that use the real alloys. */
final class AlloyFixtures {
    private AlloyFixtures() {
    }

    static Map<String, Object> shippedYaml() {
        try (InputStream in = AlloyFixtures.class.getResourceAsStream("/alloys.yml")) {
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static AlloyConfigParser.Result shipped() {
        return AlloyConfigParser.parse(shippedYaml(), AlloyVocabulary.ANY);
    }

    static AlloyRules rules() {
        return shipped().rules();
    }

    static Map<String, Object> yaml(String text) {
        return new Yaml().load(text);
    }
}
