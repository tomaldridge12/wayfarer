package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlacedTrophyTest {
    private static final UUID TOM = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void bareIdsFromOlderChunkDataAreBanners() {
        PlacedTrophy t = PlacedTrophy.parse("zombie");
        assertTrue(t.isBanner());
        assertEquals("zombie", t.reward().id());
        assertEquals("zombie", t.encode());
    }

    @Test
    void headsRoundTripWithEarnerAndDate() {
        PlacedTrophy head = PlacedTrophy.head("zombie", TOM, 1_700_000_000_000L);
        assertEquals("head:zombie@" + TOM + "@1700000000000", head.encode());
        assertEquals(head, PlacedTrophy.parse(head.encode()));
    }

    @Test
    void aDamagedEarnerKeepsTheReward() {
        PlacedTrophy t = PlacedTrophy.parse("head:zombie@not-a-uuid@x");
        assertTrue(t.isHead());
        assertNull(t.earner());
    }
}
