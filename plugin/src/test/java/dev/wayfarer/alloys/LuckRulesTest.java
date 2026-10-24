package dev.wayfarer.alloys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Shakudo's luck: Fortune one higher on its tools, a chance of extra loot on its weapon kills. */
class LuckRulesTest {
    private final AlloyRules rules = AlloyFixtures.rules();

    @Test
    void shakudoToolsMineAsIfFortuneWereOneHigher() {
        assertEquals(1, rules.fortune("shakudo", 0));
        assertEquals(4, rules.fortune("shakudo", 3));
    }

    @Test
    void otherAlloysGetNoFortune() {
        assertEquals(0, rules.fortune("steel", 0));
        assertEquals(3, rules.fortune("adamant", 3));
        assertEquals(2, rules.fortune("tin", 2));
    }

    @Test
    void shakudoWeaponKillsHaveAOneInThreeChanceOfExtraLoot() {
        assertTrue(rules.extraLoot("shakudo", 0.0));
        assertTrue(rules.extraLoot("shakudo", 0.32));
        assertFalse(rules.extraLoot("shakudo", 0.34));
        assertFalse(rules.extraLoot("electrum", 0.0));
    }
}
