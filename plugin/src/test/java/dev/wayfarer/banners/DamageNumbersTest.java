package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DamageNumbersTest {
    @Test
    void unboostedHitHasNoExtra() {
        assertEquals(new DamageNumbers.Split(4.92, 0), DamageNumbers.split(4.92, 4.92, 20));
    }

    @Test
    void boostedHitSplitsBaseAndExtra() {
        DamageNumbers.Split s = DamageNumbers.split(4.92, 5.412, 20);
        assertEquals(4.92, s.base(), 1e-9);
        assertEquals(0.492, s.extra(), 1e-9);
    }

    @Test
    void killingBlowShowsOnlyTheHealthThatWasLeft() {
        assertEquals(new DamageNumbers.Split(3, 0), DamageNumbers.split(4.92, 5.41, 3));
        DamageNumbers.Split s = DamageNumbers.split(4.92, 9.84, 7);
        assertEquals(4.92, s.base(), 1e-9);
        assertEquals(2.08, s.extra(), 1e-9);
    }

    @Test
    void figuresUseOneDecimalWithoutTrailingZero() {
        assertEquals("4.9", DamageNumbers.format(4.92));
        assertEquals("10", DamageNumbers.format(9.96));
        assertEquals("0.5", DamageNumbers.format(0.5));
        assertEquals("3", DamageNumbers.format(3.0));
        assertEquals("0", DamageNumbers.format(0.01));
    }
}
