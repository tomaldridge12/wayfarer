package dev.wayfarer.bosses;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/** Every valid boss from one config load, the star scale, and a message for everything rejected. */
public record BossRoster(Map<String, BossDefinition> bosses, StarScale stars, SpawnRules.Settings spawning,
        List<String> problems) {
    public BossRoster(Map<String, BossDefinition> bosses, StarScale stars, List<String> problems) {
        this(bosses, stars, SpawnRules.Settings.defaults(), problems);
    }

    public static BossRoster empty() {
        return new BossRoster(Map.of(), StarScale.defaults(), List.of());
    }

    public Optional<BossDefinition> find(String id) {
        return Optional.ofNullable(bosses.get(id));
    }

    /** A variant chosen evenly at random; {@code random} gives values in [0, 1). */
    public static BossVariant pickVariant(BossDefinition boss, DoubleSupplier random) {
        int i = (int) Math.floor(random.getAsDouble() * boss.variants().size());
        return boss.variants().get(Math.min(i, boss.variants().size() - 1));
    }
}
