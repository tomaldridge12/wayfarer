package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Vanilla's XP maths: points per level, points held, taking points, and the price in levels. */
class XpPointsTest {
    @Test
    void pointsToReachALevelMatchVanilla() {
        assertEquals(0, XpPoints.toReach(0));
        assertEquals(7, XpPoints.toReach(1));
        assertEquals(352, XpPoints.toReach(16));
        assertEquals(394, XpPoints.toReach(17));
        assertEquals(1395, XpPoints.toReach(30));
        assertEquals(1507, XpPoints.toReach(31));
        assertEquals(1628, XpPoints.toReach(32));
        assertEquals(5345, XpPoints.toReach(50));
    }

    @Test
    void theCostOfOneLevelStepsUpAtTheBoundaries() {
        assertEquals(7, XpPoints.forLevel(0));
        assertEquals(37, XpPoints.forLevel(15));
        assertEquals(42, XpPoints.forLevel(16));
        assertEquals(112, XpPoints.forLevel(30));
        assertEquals(121, XpPoints.forLevel(31));
        for (int l = 0; l < 60; l++) {
            assertEquals(XpPoints.toReach(l + 1) - XpPoints.toReach(l), XpPoints.forLevel(l), "level " + l);
        }
    }

    @Test
    void pointsHeldAddTheProgressThroughTheCurrentLevel() {
        assertEquals(0, XpPoints.held(0, 0f));
        assertEquals(352, XpPoints.held(16, 0f));
        assertEquals(352 + 21, XpPoints.held(16, 0.5f));
        assertEquals(1395 + 56, XpPoints.held(30, 0.5f));
    }

    @Test
    void pointsMakeTheBarAndBack() {
        for (int points : new int[] {0, 6, 7, 351, 352, 393, 394, 1506, 1507, 5344, 5345, 9000}) {
            XpPoints.Bar bar = XpPoints.fromPoints(points);
            assertEquals(points, XpPoints.held(bar.level(), bar.progress()), "points " + points);
            assertTrue(bar.progress() >= 0f && bar.progress() < 1f);
        }
        assertEquals(16, XpPoints.fromPoints(352).level());
        assertEquals(15, XpPoints.fromPoints(351).level());
    }

    @Test
    void takingPointsAcrossLevelBoundariesLeavesTheRightLevelAndProgress() {
        // Level 17 exactly (394) minus 42 points is level 16 exactly (352).
        XpPoints.Bar a = XpPoints.after(17, 0f, 42).orElseThrow();
        assertEquals(16, a.level());
        assertEquals(0f, a.progress(), 0.001f);
        // Level 31 exactly (1507) minus 112 points reaches level 30 exactly (1395).
        XpPoints.Bar b = XpPoints.after(31, 0f, 112).orElseThrow();
        assertEquals(30, b.level());
        assertEquals(0f, b.progress(), 0.001f);
        // Half way through level 5 (55 + 9) minus 3 points stays in level 5.
        XpPoints.Bar c = XpPoints.after(5, 0.5f, 3).orElseThrow();
        assertEquals(5, c.level());
        assertEquals(XpPoints.held(5, 0.5f) - 3, XpPoints.held(c.level(), c.progress()));
        // A big price drops many levels.
        XpPoints.Bar d = XpPoints.after(30, 0f, 1395 - 27).orElseThrow();
        assertEquals(3, d.level());
    }

    @Test
    void takingMoreThanHeldIsRefusedAndTakingExactlyHeldEmptiesTheBar() {
        assertEquals(Optional.empty(), XpPoints.after(5, 0f, 56));
        XpPoints.Bar empty = XpPoints.after(5, 0f, 55).orElseThrow();
        assertEquals(0, empty.level());
        assertEquals(0f, empty.progress());
        assertEquals(Optional.empty(), XpPoints.after(0, 0f, 1));
    }

    @Test
    void aPriceReadsInYourLevels() {
        // At level 5 (55 points), 27 points leaves 28: level 3, so about 2 levels.
        assertEquals("27 XP · about 2 of your levels", XpPoints.describe(5, 0f, 27));
        // The same price at level 40 is a small slice of one level.
        assertEquals("27 XP · under 1 level", XpPoints.describe(40, 0f, 27));
        // The same price at level 40 with 112-point steps near 30: 315 points is roughly 3 levels at level 30.
        assertEquals("315 XP · about 3 of your levels", XpPoints.describe(30, 0f, 315));
    }

    @Test
    void aPriceOfAboutOneLevelReadsInTheSingular() {
        assertEquals("7 XP · about 1 level", XpPoints.describe(1, 0f, 7));
    }

    @Test
    void thePriceInLevelsIsCappedAtWhatThePlayerHolds() {
        assertEquals("315 XP · about 5 of your levels", XpPoints.describe(5, 0f, 315));
        assertEquals("315 XP · under 1 level", XpPoints.describe(0, 0f, 315));
    }
}
