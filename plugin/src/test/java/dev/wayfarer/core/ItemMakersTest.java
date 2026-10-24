package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Module-made items by key, with fallbacks when the maker is missing. */
class ItemMakersTest {
    @Test
    void aRegisteredPrefixMakesItsItems() {
        ItemMakers<String> makers = new ItemMakers<>();
        makers.register("alloys", "alloy", id -> id.startsWith("steel") ? Optional.of("made " + id) : Optional.empty());
        assertEquals(Optional.of("made steel_axe"), makers.make("alloy:steel_axe"));
        assertEquals(Optional.empty(), makers.make("alloy:tin_axe"));
    }

    @Test
    void unknownPrefixesAndBadKeysAreEmpty() {
        ItemMakers<String> makers = new ItemMakers<>();
        makers.register("alloys", "alloy", id -> Optional.of(id));
        assertEquals(Optional.empty(), makers.make("mark:dune"));
        assertEquals(Optional.empty(), makers.make("steel_axe"));
        assertEquals(Optional.empty(), makers.make(":steel_axe"));
    }

    @Test
    void droppingAnOwnerForgetsItsMakers() {
        ItemMakers<String> makers = new ItemMakers<>();
        makers.register("alloys", "alloy", id -> Optional.of(id));
        makers.drop("alloys");
        assertEquals(Optional.empty(), makers.make("alloy:steel_axe"));
    }

    @Test
    void anotherOwnerCannotTakeAPrefix() {
        ItemMakers<String> makers = new ItemMakers<>();
        makers.register("alloys", "alloy", id -> Optional.of(id));
        assertThrows(IllegalArgumentException.class, () -> makers.register("banners", "alloy", id -> Optional.of(id)));
    }
}
