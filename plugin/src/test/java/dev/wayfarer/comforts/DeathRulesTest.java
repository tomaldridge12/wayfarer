package dev.wayfarer.comforts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** What a death keeps: which slots, and putting the hotbar back where it was. */
class DeathRulesTest {
    private static final java.util.function.IntPredicate EVERYTHING = slot -> true;

    @Test
    void everySwitchOnKeepsHotbarArmourAndOffhandButNeverTheMainInventory() {
        List<Integer> kept = DeathRules.keptSlots(true, true, true, EVERYTHING);
        assertEquals(IntStream.concat(IntStream.range(0, 9), IntStream.rangeClosed(36, 40)).boxed().toList(), kept);
        assertTrue(kept.stream().noneMatch(slot -> slot >= 9 && slot <= 35), "the main inventory drops");
    }

    @Test
    void eachSwitchKeepsOnlyItsOwnSlots() {
        assertEquals(IntStream.range(0, 9).boxed().toList(), DeathRules.keptSlots(true, false, false, EVERYTHING));
        assertEquals(List.of(36, 37, 38, 39), DeathRules.keptSlots(false, true, false, EVERYTHING));
        assertEquals(List.of(40), DeathRules.keptSlots(false, false, true, EVERYTHING));
        assertEquals(List.of(), DeathRules.keptSlots(false, false, false, EVERYTHING));
    }

    @Test
    void aSlotThatIsEmptyOrCursedWithVanishingIsNotKept() {
        Set<Integer> keepable = Set.of(0, 3, 38);
        assertEquals(List.of(0, 3, 38), DeathRules.keptSlots(true, true, true, keepable::contains));
    }

    @Test
    void theHotbarGoesBackToItsOldSlots() {
        List<String> now = Arrays.asList("pick", "sword", "food", null, null, null, null, null, null);
        Optional<List<String>> layout = DeathRules.hotbarLayout(List.of(0, 4, 8), List.of("pick", "sword", "food"), now);
        assertEquals(Arrays.asList("pick", null, null, null, "sword", null, null, null, "food"), layout.orElseThrow());
    }

    @Test
    void aHotbarAnotherPluginChangedIsLeftAlone() {
        List<String> extra = Arrays.asList("pick", "sword", "kit", null, null, null, null, null, null);
        assertTrue(DeathRules.hotbarLayout(List.of(0, 4), List.of("pick", "sword"), extra).isEmpty(), "an item that was not kept");
        List<String> missing = Arrays.asList("pick", null, null, null, null, null, null, null, null);
        assertTrue(DeathRules.hotbarLayout(List.of(0, 4), List.of("pick", "sword"), missing).isEmpty(), "a kept item is gone");
        List<String> reordered = Arrays.asList("sword", "pick", null, null, null, null, null, null, null);
        assertTrue(DeathRules.hotbarLayout(List.of(0, 4), List.of("pick", "sword"), reordered).isEmpty(), "order changed");
    }

    @Test
    void anEmptyKeptHotbarAndBadInputGiveANormalAnswer() {
        List<String> empty = Arrays.asList(null, null, null, null, null, null, null, null, null);
        assertEquals(empty, DeathRules.hotbarLayout(List.<Integer>of(), List.<String>of(), empty).orElseThrow());
        assertFalse(DeathRules.hotbarLayout(List.of(0), List.of("a"), Arrays.asList("a", null)).isPresent(), "not nine slots");
        assertFalse(DeathRules.hotbarLayout(List.of(9), List.of("a"),
                Arrays.asList("a", null, null, null, null, null, null, null, null)).isPresent(), "slot outside the hotbar");
        assertFalse(DeathRules.hotbarLayout(List.of(1, 1), List.of("a", "b"),
                Arrays.asList("a", "b", null, null, null, null, null, null, null)).isPresent(), "two stacks in one slot");
    }
}
