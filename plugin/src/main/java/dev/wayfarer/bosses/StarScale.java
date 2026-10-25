package dev.wayfarer.bosses;

import java.util.List;

/** How each star from 1 to 5 multiplies a boss's health and damage. */
public record StarScale(List<Double> health, List<Double> damage) {
    public static final int MAX = 5;

    public static StarScale defaults() {
        return new StarScale(List.of(1.0, 1.75, 3.0, 5.0, 8.0), List.of(1.0, 1.3, 1.7, 2.2, 3.0));
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
