package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The boss mark's text form, with and without the guardian flag. */
class BossMarkTest {
    @Test
    void aGuardianMarkRoundTrips() {
        BossMark mark = new BossMark("bone_marshal", "bone_marshal", "bone_marshal", 3, 10, 64, -20, true);
        assertEquals(Optional.of(mark), BossMark.parse(mark.encode()));
    }

    @Test
    void aRoamingMarkRoundTripsAsNotAGuardian() {
        BossMark mark = new BossMark("rotting_warlord", "rotting_warlord", "plague_bearer", 2, 0, 70, 0);
        assertFalse(BossMark.parse(mark.encode()).orElseThrow().guardian());
    }

    @Test
    void marksWrittenBeforeTheFlagReadAsNotAGuardian() {
        BossMark old = BossMark.parse("bone_marshal;bone_marshal;bone_marshal;4;1,2,3").orElseThrow();
        assertFalse(old.guardian());
        assertEquals(4, old.stars());
    }

    @Test
    void brokenMarksAreEmpty() {
        assertTrue(BossMark.parse("bone_marshal;3").isEmpty());
        assertTrue(BossMark.parse("a;b;c;x;1,2,3;guardian").isEmpty());
    }
}
