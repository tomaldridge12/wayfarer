package dev.wayfarer.bosses;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.DoubleSupplier;

/** Where and when bosses appear on their own. No server types. */
public final class SpawnRules {
    /**
     * One boss's natural and guardian spawning. {@code biomes} empty with {@code anyBiome} false means no natural
     * spawns; {@code entity} is the vanilla mob whose natural spawn can turn into the boss.
     */
    public record BossSpawn(String entity, Set<String> biomes, boolean anyBiome, double chance, boolean nightOnly,
            Set<String> structures) {
        public static BossSpawn none() {
            return new BossSpawn("", Set.of(), false, 0, false, Set.of());
        }

        boolean natural() {
            return chance > 0 && (anyBiome || !biomes.isEmpty());
        }
    }

    /** Distance bands from world spawn: beyond each threshold a boss rolls one more star. */
    public record Settings(List<Integer> starBands, int guardianMinStars, double extraStarChance) {
        public static Settings defaults() {
            return new Settings(List.of(750, 2000, 4000, 7000), 3, 0.25);
        }
    }

    private SpawnRules() {
    }

    /** Whether this natural spawn becomes the boss now. */
    public static boolean naturalSpawn(BossSpawn spawn, String entity, String biome, boolean night, boolean alive,
            long cooldownLeftMillis, DoubleSupplier random) {
        return spawn.natural() && spawn.entity().equals(entity) && (spawn.anyBiome() || spawn.biomes().contains(biome))
                && (!spawn.nightOnly() || night) && !alive && cooldownLeftMillis <= 0
                && random.getAsDouble() < spawn.chance();
    }

    /** Whether a player inside this structure wakes its guardian. */
    public static boolean guardian(BossSpawn spawn, String structure, boolean alive, long bossCooldownLeftMillis,
            long structureCooldownLeftMillis) {
        return spawn.structures().contains(structure) && !alive && bossCooldownLeftMillis <= 0
                && structureCooldownLeftMillis <= 0;
    }

    /** Stars by distance from world spawn, sometimes one more; guardians never below their minimum. */
    public static int stars(double distanceFromSpawn, boolean guardian, Settings settings, DoubleSupplier random) {
        int stars = 1;
        for (int band : settings.starBands()) {
            if (distanceFromSpawn >= band) {
                stars++;
            }
        }
        if (random.getAsDouble() < settings.extraStarChance()) {
            stars++;
        }
        if (guardian) {
            stars = Math.max(stars, settings.guardianMinStars());
        }
        return StarScale.clamp(stars);
    }

    /** Milliseconds left before something used at {@code lastMillis} may happen again. */
    public static long cooldownLeft(long lastMillis, int cooldownMinutes, long nowMillis) {
        return Math.max(0, lastMillis + cooldownMinutes * 60_000L - nowMillis);
    }

    static Map<String, Object> asMap(Object value) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
        return m;
    }
}
