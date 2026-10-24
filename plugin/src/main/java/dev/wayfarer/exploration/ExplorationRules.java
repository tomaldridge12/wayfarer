package dev.wayfarer.exploration;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Exploration rules: what a Wayfinder pointing costs, the search cooldown, which guardian a Guardian's Chart points
 * at, whether safe surface stops a spawn, and the needle that shows the way. No server types.
 */
public final class ExplorationRules {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /** XP levels per pointing: rare targets by name, else the default for structures or biomes. */
    public record Prices(int structure, int biome, Map<String, Integer> rare) {
        public int of(String target, boolean structure) {
            String path = target.substring(target.indexOf(':') + 1);
            return rare.getOrDefault(path, rare.getOrDefault(target, structure ? this.structure : biome));
        }
    }

    private ExplorationRules() {
    }

    /** Milliseconds left on the player's search cooldown; 0 when they may search. */
    public static long cooldownLeft(long lastSearch, int cooldownSeconds, long now) {
        return Math.max(0, lastSearch + cooldownSeconds * 1000L - now);
    }

    /**
     * The guardian sites a chart may point at: structures whose guardian the player has not beaten (by their
     * {@code defeat:} counters).
     */
    public static Set<String> unbeaten(Map<String, String> guardians, Map<String, Long> counters) {
        Set<String> out = new java.util.LinkedHashSet<>();
        guardians.forEach((structure, boss) -> {
            if (counters.getOrDefault("defeat:" + boss, 0L) <= 0) {
                out.add(structure);
            }
        });
        return out;
    }

    /** The nearest of several found sites (structure key to distance in blocks), or empty when none was found. */
    public static Optional<String> nearest(Map<String, Double> found) {
        return found.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
    }

    /**
     * Safe surface: after the first dragon falls, a natural hostile spawn in the Overworld is stopped where the sky is
     * open (sky light 15) or above sea level. Everything else (caves, other dimensions, bosses, spawners) is left alone.
     */
    public static boolean stopsSpawn(boolean on, boolean overworld, boolean natural, boolean hostile, int skyLight, int y, int seaLevel) {
        return on && overworld && natural && hostile && (skyLight >= 15 || y > seaLevel);
    }

    /**
     * The arrow pointing from where the player looks to the target: {@code yaw} is Minecraft's (0 south, 90 west),
     * {@code dx}/{@code dz} the way to the target.
     */
    public static String arrow(float yaw, double dx, double dz) {
        double target = Math.toDegrees(Math.atan2(-dx, dz));
        double diff = ((target - yaw) % 360 + 540) % 360 - 180;
        int sector = (int) Math.floorMod(Math.round(diff / 45.0), 8);
        return ARROWS[sector];
    }
}
