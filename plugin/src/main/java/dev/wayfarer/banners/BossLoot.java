package dev.wayfarer.banners;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.DoubleSupplier;

/** What a boss defeat gives each player who damaged it. No server types. */
public final class BossLoot {
    /**
     * Chances by stars (index 0 is one star), XP per star, and materials per player as {item: [min, max]} per star.
     * {@code countTowardBase} makes a mini boss kill also count toward its base family.
     */
    public record Settings(List<Double> ascendedChance, List<Double> legendaryChance, int xpPerStar,
            Map<String, List<Integer>> materials, boolean countTowardBase) {
        public static Settings defaults() {
            return new Settings(List.of(0.05, 0.12, 0.20, 0.27, 0.35), List.of(0.01, 0.025, 0.04, 0.06, 0.08), 60,
                    Map.of("emerald", List.of(2, 4)), false);
        }
    }

    /** One player's rolls: whether they get an Ascended relic and the Legendary. */
    public record Rolls(boolean ascended, boolean legendary) {
    }

    private BossLoot() {
    }

    /** Ascended only drops from mini bosses (those with a base family). */
    public static Rolls roll(Settings s, int stars, boolean miniBoss, DoubleSupplier random) {
        int i = Math.max(1, Math.min(5, stars)) - 1;
        boolean ascended = miniBoss && random.getAsDouble() < s.ascendedChance().get(i);
        boolean legendary = random.getAsDouble() < s.legendaryChance().get(i);
        return new Rolls(ascended, legendary);
    }

    /** One of the base family's Ascended relics, chosen evenly. */
    public static Optional<RelicDefinition> pickAscended(BannerSet set, String baseFamily, DoubleSupplier random) {
        List<RelicDefinition> options = set.relics().values().stream()
                .filter(r -> r.tier() == 3 && r.family().equals(baseFamily)).toList();
        if (options.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(options.get(Math.min(options.size() - 1, (int) (random.getAsDouble() * options.size()))));
    }

    /** How many of a material one player gets: a random amount between min and max, times the stars. */
    public static int materialAmount(List<Integer> range, int stars, DoubleSupplier random) {
        int min = range.get(0);
        int max = Math.max(min, range.get(1));
        return (min + (int) Math.floor(random.getAsDouble() * (max - min + 1))) * Math.max(1, Math.min(5, stars));
    }
}
