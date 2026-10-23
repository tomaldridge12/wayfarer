package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IndicatorBudgetTest {
    @Test
    void newIndicatorsAreSkippedAtTheCapUntilOldOnesExpire() {
        IndicatorBudget budget = new IndicatorBudget(25, 20);
        for (int i = 0; i < 25; i++) {
            assertTrue(budget.tryStart(100));
        }
        assertFalse(budget.tryStart(100));
        assertFalse(budget.tryStart(119));
        assertEquals(25, budget.active(119));
        assertTrue(budget.tryStart(120)); // the first batch has lived its 20 ticks
        assertEquals(1, budget.active(120));
    }

    @Test
    void staggeredIndicatorsFreeSlotsOneByOne() {
        IndicatorBudget budget = new IndicatorBudget(2, 20);
        assertTrue(budget.tryStart(0));
        assertTrue(budget.tryStart(10));
        assertFalse(budget.tryStart(15));
        assertTrue(budget.tryStart(20));
        assertFalse(budget.tryStart(25));
        assertTrue(budget.tryStart(30));
    }
}
