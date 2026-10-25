package dev.wayfarer.bosses;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FightTest {
    @Test
    void starsAddAbilitiesAtThreeAndFive() {
        assertEquals(List.of(Ability.POISON_CLOUD, Ability.SUMMON_MINIONS),
                new AbilityPlanner(List.of(Ability.POISON_CLOUD, Ability.SUMMON_MINIONS), 2).abilities());
        assertEquals(List.of(Ability.POISON_CLOUD, Ability.SUMMON_MINIONS, Ability.CHARGE),
                new AbilityPlanner(List.of(Ability.POISON_CLOUD, Ability.SUMMON_MINIONS), 3).abilities());
        assertEquals(List.of(Ability.CHARGE, Ability.GROUND_SLAM, Ability.SUMMON_MINIONS, Ability.POISON_CLOUD),
                new AbilityPlanner(List.of(Ability.CHARGE, Ability.GROUND_SLAM, Ability.ENRAGE), 5).abilities());
    }

    @Test
    void abilitiesNeedTheirRangeAndCooldown() {
        AbilityPlanner planner = new AbilityPlanner(List.of(Ability.GROUND_SLAM, Ability.CHARGE), 1);
        assertEquals(Optional.of(Ability.GROUND_SLAM), planner.next(1000, 3, false));
        assertEquals(Optional.of(Ability.CHARGE), planner.next(1000, 10, false));
        assertEquals(Optional.empty(), planner.next(1000, 40, false)); // out of every range
        assertEquals(Optional.empty(), planner.next(1000, Double.NaN, false)); // no target
        planner.used(Ability.GROUND_SLAM, 1000);
        assertEquals(Optional.empty(), planner.next(1030, 10, false)); // global cooldown (60 ticks)
        assertEquals(Optional.of(Ability.CHARGE), planner.next(1060, 10, false));
        assertEquals(Optional.empty(), planner.next(1100, 3, false)); // slam still cooling (140 ticks)
        assertEquals(Optional.of(Ability.GROUND_SLAM), planner.next(1140, 3, false));
    }

    private static void used(AbilityPlanner planner, long tick) {
        planner.abilities().forEach(a -> planner.used(a, tick));
    }

    @Test
    void moreStarsAndPhaseTwoShortenCooldowns() {
        AbilityPlanner fiveStar = new AbilityPlanner(List.of(Ability.GROUND_SLAM, Ability.CHARGE, Ability.SUMMON_MINIONS, Ability.POISON_CLOUD), 5);
        used(fiveStar, 0);
        assertEquals(Optional.empty(), fiveStar.next(83, 3, false));
        assertEquals(Optional.of(Ability.GROUND_SLAM), fiveStar.next(84, 3, false)); // 140 * 0.6
        AbilityPlanner enraged = new AbilityPlanner(List.of(Ability.GROUND_SLAM, Ability.CHARGE, Ability.SUMMON_MINIONS, Ability.POISON_CLOUD), 5);
        used(enraged, 0);
        assertEquals(Optional.of(Ability.GROUND_SLAM), enraged.next(63, 3, true)); // 140 * 0.6 * 0.75
    }

    @Test
    void leastRecentlyUsedReadyAbilityGoesFirst() {
        AbilityPlanner planner = new AbilityPlanner(List.of(Ability.SUMMON_MINIONS, Ability.POISON_CLOUD), 1);
        assertEquals(Optional.of(Ability.SUMMON_MINIONS), planner.next(1000, 5, false));
        planner.used(Ability.SUMMON_MINIONS, 1000);
        assertEquals(Optional.of(Ability.POISON_CLOUD), planner.next(1060, 5, false));
    }

    @Test
    void phaseTwoAtHalfHealth() {
        assertFalse(FightRules.phaseTwo(51, 100));
        assertTrue(FightRules.phaseTwo(50, 100));
    }

    @Test
    void leashWalksHomeThenTeleportsWhenFarOrStuck() {
        assertEquals(FightRules.LeashAction.STAY, FightRules.leash(30, 0));
        assertEquals(FightRules.LeashAction.WALK_HOME, FightRules.leash(33, 0));
        assertEquals(FightRules.LeashAction.TELEPORT_HOME, FightRules.leash(33, FightRules.STUCK_TICKS));
        assertEquals(FightRules.LeashAction.TELEPORT_HOME, FightRules.leash(49, 0));
    }

    @Test
    void abandonedBossesHealAfterThirtySeconds() {
        assertFalse(FightRules.shouldHeal(590));
        assertTrue(FightRules.shouldHeal(600));
    }

    @Test
    void minionsScaleWithStars() {
        assertEquals(2, FightRules.minionCount(1));
        assertEquals(3, FightRules.minionCount(3));
        assertEquals(4, FightRules.minionCount(5));
    }
}
