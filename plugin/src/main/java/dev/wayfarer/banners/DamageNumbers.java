package dev.wayfarer.banners;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The figures shown in a damage popup. No server types. */
public final class DamageNumbers {
    /** Damage the hit would have done without a banner, and the extra the banner added. */
    public record Split(double base, double extra) {
        public double total() {
            return base + extra;
        }
    }

    private DamageNumbers() {
    }

    /**
     * @param before final damage before the banner bonus (equal to {@code after} for an unboosted hit)
     * @param after final damage the hit does
     * @param health the victim's health plus absorption before the hit; damage past it is not shown
     */
    public static Split split(double before, double after, double health) {
        double base = clamp(Math.min(before, health));
        double total = clamp(Math.min(after, health));
        return new Split(base, Math.max(0, total - base));
    }

    /** One decimal place, without a trailing ".0": 4.9, 10, 0.5. */
    public static String format(double damage) {
        BigDecimal rounded = BigDecimal.valueOf(damage).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
        return rounded.scale() <= 0 ? rounded.setScale(0).toPlainString() : rounded.toPlainString();
    }

    private static double clamp(double v) {
        return Math.max(0, v);
    }
}
