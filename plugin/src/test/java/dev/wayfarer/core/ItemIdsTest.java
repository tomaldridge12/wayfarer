package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ItemIdsTest {
    private final ItemIds ids = new ItemIds();

    @Test
    void aKnownIdOfARegisteredKindCountsUnderTheWayfarerNamespace() {
        ids.register("collections", "fish", () -> Set.of("arapaima"));
        assertEquals(Optional.of("wayfarer:fish/arapaima"), ids.target("fish/arapaima"));
    }

    @Test
    void unknownIdsKindsAndShapesCountAsNothing() {
        ids.register("collections", "fish", () -> Set.of("arapaima"));
        assertEquals(Optional.empty(), ids.target("fish/goldfish"));
        assertEquals(Optional.empty(), ids.target("curio/moss_heart"));
        assertEquals(Optional.empty(), ids.target("arapaima"));
    }

    @Test
    void aDroppedOwnersItemsCountAsNothing() {
        ids.register("curios", "curio", () -> Set.of("moss_heart"));
        ids.drop("curios");
        assertEquals(Optional.empty(), ids.target("curio/moss_heart"));
    }

    @Test
    void idsAreListedSortedByKind() {
        ids.register("collections", "fish", () -> Set.of("pike", "arapaima"));
        assertEquals(java.util.List.of("arapaima", "pike"), ids.ids("fish"));
        assertEquals(java.util.List.of(), ids.ids("treasure"));
    }

    @Test
    void aKindHasOneOwner() {
        ids.register("curios", "curio", () -> Set.of("moss_heart"));
        assertThrows(IllegalArgumentException.class, () -> ids.register("objectives", "curio", () -> Set.of("moss_heart")));
    }
}
