package dev.wayfarer.objectives;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/** The shipped objectives.yml with a stand-in for the server's tags, records and particles. */
final class ObjectiveFixtures {
    static final ObjectiveVocabulary SERVER = new ObjectiveVocabulary() {
        @Override
        public Set<String> tag(String tag) {
            return switch (tag) {
                case "minecraft:hoes" -> Set.of("wooden_hoe", "stone_hoe", "iron_hoe", "golden_hoe", "diamond_hoe", "netherite_hoe");
                case "minecraft:decorated_pot_sherds" -> Set.of("angler_pottery_sherd", "archer_pottery_sherd", "arms_up_pottery_sherd");
                default -> Set.of();
            };
        }

        @Override
        public Set<String> group(String group) {
            return group.equals("music_discs") ? Set.of("music_disc_13", "music_disc_cat", "music_disc_pigstep") : Set.of();
        }

        @Override
        public boolean isParticle(String key) {
            return !key.startsWith("nope");
        }
    };

    private ObjectiveFixtures() {
    }

    static Map<String, Object> shippedYaml() {
        try (InputStream in = ObjectiveFixtures.class.getResourceAsStream("/objectives.yml")) {
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static ObjectiveConfigParser.Result shipped() {
        return ObjectiveConfigParser.parse(shippedYaml(), SERVER);
    }
}
