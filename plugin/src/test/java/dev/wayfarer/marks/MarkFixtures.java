package dev.wayfarer.marks;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/** The shipped marks.yml with a small stand-in for the server's tags. */
final class MarkFixtures {
    /** Knows every item; tags hold a couple of made-up members; ExcellentEnchants is installed. */
    static final MarkVocabulary SERVER = new MarkVocabulary() {
        @Override
        public boolean isItem(String key) {
            return !key.startsWith("nope");
        }

        @Override
        public boolean isEnchant(String key) {
            return key.startsWith("excellentenchants:");
        }

        @Override
        public Set<String> tag(String tag) {
            return switch (tag) {
                case "minecraft:wool" -> Set.of("white_wool", "black_wool");
                case "minecraft:logs" -> Set.of("oak_log", "spruce_log", "stripped_oak_log");
                case "minecraft:coal_ores" -> Set.of("coal_ore", "deepslate_coal_ore");
                default -> tag.startsWith("minecraft:") && tag.endsWith("_ores") ? Set.of(tag.substring(10, tag.length() - 1)) : Set.of();
            };
        }
    };

    private MarkFixtures() {
    }

    static Map<String, Object> shippedYaml() {
        try (InputStream in = MarkFixtures.class.getResourceAsStream("/marks.yml")) {
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static MarkConfigParser.Result shipped() {
        return MarkConfigParser.parse(shippedYaml(), SERVER);
    }

    static MarkRules rules() {
        return shipped().rules();
    }
}
