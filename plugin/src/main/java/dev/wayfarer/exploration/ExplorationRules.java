package dev.wayfarer.exploration;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Exploration rules: what a Wayfinder pointing costs, the search cooldown, which guardian a Guardian's Chart points
 * at, whether safe surface stops a spawn, and the needle that shows the way. No server types.
 */
public final class ExplorationRules {
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    /**
     * XP points per pointing: rare targets by name, else the default for structures or biomes; a biome the player has
     * not found costs {@code unfoundMultiplier} times as much.
     */
    public record Prices(int structure, int biome, Map<String, Integer> rare, double unfoundMultiplier) {
        public int of(String target, boolean structure) {
            String path = target.substring(target.indexOf(':') + 1);
            return rare.getOrDefault(path, rare.getOrDefault(target, structure ? this.structure : biome));
        }

        /** A biome's price: its own price, times the multiplier if the player has not found it. */
        public int biome(String target, boolean found) {
            int base = of(target, false);
            return found ? base : (int) Math.round(base * unfoundMultiplier);
        }

        /** The multiplier as written: "2" or "1.5". */
        public String multiplierText() {
            return unfoundMultiplier == Math.rint(unfoundMultiplier) ? String.valueOf((long) unfoundMultiplier) : String.valueOf(unfoundMultiplier);
        }
    }

    /** One biome on the Wayfinder's list. */
    public record BiomeOption(String key, String name, boolean found) {
    }

    /** What a finished search settles: whether to charge the player, and whether the search cooldown starts. */
    public record Settlement(boolean charge, boolean cooldown) {
    }

    private ExplorationRules() {
    }

    /** The biome list: found biomes first, then by name; each group in name order. */
    public static List<BiomeOption> order(Collection<BiomeOption> options) {
        return options.stream().sorted(Comparator.comparing((BiomeOption o) -> !o.found()).thenComparing(o -> o.name().toLowerCase(Locale.ROOT))
                .thenComparing(BiomeOption::key)).toList();
    }

    /**
     * What a finished search costs. A failed search charges nothing but still starts the cooldown, so slow searches
     * cannot be spammed; a free pointing (a curio's charge) never uses the cooldown and is only spent on success.
     */
    public static Settlement settle(boolean found, boolean free) {
        return new Settlement(found && !free, !free);
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
