package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.DoubleUnaryOperator;
import org.junit.jupiter.api.Test;

class DamageSolverTest {
    /** Vanilla's armour formula for 20 armour and 8 toughness, which is not linear in damage. */
    private static final DoubleUnaryOperator ARMOUR = d -> {
        double armour = 20;
        double toughness = 8;
        double effective = Math.clamp(armour - d / (2 + toughness / 4), armour * 0.2, 20);
        return d * (1 - effective / 25);
    };

    @Test
    void findsRawDamageGivingTheWantedFinalDamageThroughArmour() {
        double raw = 9;
        double before = ARMOUR.applyAsDouble(raw);
        double target = before * 1.10;
        assertEquals(target, ARMOUR.applyAsDouble(DamageSolver.rawFor(target, raw, ARMOUR)), 1e-6);
        double lower = before * 0.90;
        assertEquals(lower, ARMOUR.applyAsDouble(DamageSolver.rawFor(lower, raw, ARMOUR)), 1e-6);
    }

    @Test
    void zeroTargetGivesZeroAndUnreachableTargetLeavesTheHitAlone() {
        assertEquals(0, DamageSolver.rawFor(0, 5, ARMOUR));
        assertEquals(5, DamageSolver.rawFor(10, 5, d -> Math.min(d, 2)));
    }
}
