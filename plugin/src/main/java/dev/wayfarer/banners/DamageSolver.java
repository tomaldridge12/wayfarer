package dev.wayfarer.banners;

import java.util.function.DoubleUnaryOperator;

/**
 * Finds the raw damage that produces a wanted final damage. The server only lets us set the raw (pre-armour)
 * damage and then reapplies armour and enchantments, which are not linear, so we search. Assumes final damage
 * never decreases as raw damage rises, which holds for vanilla armour, enchantments and resistance.
 */
public final class DamageSolver {
    private static final int STEPS = 50;

    private DamageSolver() {
    }

    /** @param finalOf final damage for a given raw damage */
    public static double rawFor(double target, double currentRaw, DoubleUnaryOperator finalOf) {
        if (target <= 0 || finalOf.applyAsDouble(0) >= target) {
            return 0;
        }
        double low = 0;
        double high = Math.max(currentRaw, 1);
        for (int i = 0; i < STEPS && finalOf.applyAsDouble(high) < target; i++) {
            low = high;
            high *= 2;
        }
        if (finalOf.applyAsDouble(high) < target) {
            return currentRaw; // unreachable (something caps the damage); leave the hit alone
        }
        for (int i = 0; i < STEPS; i++) {
            double mid = (low + high) / 2;
            if (finalOf.applyAsDouble(mid) < target) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return high;
    }
}
