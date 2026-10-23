package dev.wayfarer.bosses;

import java.util.List;

/** How each star from 1 to 5 multiplies a boss's health and damage. */
public record StarScale(List<Double> health, List<Double> damage) {
    public static final int MAX = 5;

    public static StarScale defaults() {
        return new StarScale(List.of(1.0, 1.5, 2.2, 3.0, 4.0), List.of(1.0, 1.2, 1.45, 1.75, 2.1));
    }

    public double health(int stars) {
        return health.get(clamp(stars) - 1);
    }

    public double damage(int stars) {
        return damage.get(clamp(stars) - 1);
    }

    public static int clamp(int stars) {
        return Math.max(1, Math.min(MAX, stars));
    }
}
