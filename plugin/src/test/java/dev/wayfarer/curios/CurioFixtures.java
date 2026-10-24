package dev.wayfarer.curios;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/** The shipped curios.yml with a stand-in for the server's names, and facts made to order. */
final class CurioFixtures {
    static final Set<String> POTIONS = Set.of("night_vision", "speed", "poison", "hunger", "water_breathing",
            "dolphins_grace", "slow_falling", "haste", "regeneration");
    static final Set<String> ATTRIBUTES = Set.of("luck", "armor", "movement_speed", "attack_damage");

    /** Knows real-looking names; anything starting "nope" is unknown. */
    static final CurioVocabulary SERVER = new CurioVocabulary() {
        @Override
        public boolean isPotion(String key) {
            return POTIONS.contains(key);
        }

        @Override
        public boolean isAttribute(String key) {
            return ATTRIBUTES.contains(key);
        }

        @Override
        public boolean isBlock(String key) {
            return !key.startsWith("nope") && !key.equals("flint") && !key.equals("amethyst_shard");
        }

        @Override
        public boolean isItem(String key) {
            return !key.startsWith("nope");
        }

        @Override
        public boolean isMob(String key) {
            return !key.startsWith("nope");
        }

        @Override
        public boolean isDamageCause(String key) {
            return Set.of("fall", "hot_floor", "freeze", "fire").contains(key);
        }

        @Override
        public boolean isPotionCause(String key) {
            return Set.of("attack", "potion_splash", "area_effect_cloud").contains(key);
        }

        @Override
        public Set<String> tag(String tag) {
            return tag.equals("minecraft:logs") ? Set.of("oak_log", "birch_log") : Set.of();
        }
    };

    private CurioFixtures() {
    }

    static Map<String, Object> shippedYaml() {
        try (InputStream in = CurioFixtures.class.getResourceAsStream("/curios.yml")) {
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Facts with defaults (overworld surface at y 70, day, dry, empty hands); override what a test needs. */
    record Facts(int y, boolean skyHidden, boolean underwater, String dimension, boolean night, boolean raining,
            Set<String> biomeTags, String heldTool, boolean nearWater, boolean wearingAlloy) implements CurioFacts {
        static Facts surface() {
            return new Facts(70, false, false, "overworld", false, false, Set.of(), "", false, false);
        }

        Facts at(int y, boolean skyHidden) {
            return new Facts(y, skyHidden, underwater, dimension, night, raining, biomeTags, heldTool, nearWater, wearingAlloy);
        }

        Facts in(String dimension) {
            return new Facts(y, skyHidden, underwater, dimension, night, raining, biomeTags, heldTool, nearWater, wearingAlloy);
        }

        Facts weather(boolean night, boolean raining) {
            return new Facts(y, skyHidden, underwater, dimension, night, raining, biomeTags, heldTool, nearWater, wearingAlloy);
        }

        Facts holding(String tool) {
            return new Facts(y, skyHidden, underwater, dimension, night, raining, biomeTags, tool, nearWater, wearingAlloy);
        }

        Facts biome(String... tags) {
            return new Facts(y, skyHidden, underwater, dimension, night, raining, Set.of(tags), heldTool, nearWater, wearingAlloy);
        }

        Facts wet(boolean underwater) {
            return new Facts(y, skyHidden, underwater, dimension, night, raining, biomeTags, heldTool, nearWater, wearingAlloy);
        }

        @Override
        public boolean inBiomeTag(String tag) {
            return biomeTags.contains(tag);
        }
    }
}
