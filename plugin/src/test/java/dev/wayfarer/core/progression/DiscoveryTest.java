package dev.wayfarer.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DiscoveryTest {
    private static final ProgressionEngine ENGINE = new ProgressionEngine(ProgressionSettings.defaults());
    private static final UUID TOM = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void aBiomeCountsOnceAndStaysAtOne() {
        ProgressionData data = new ProgressionData();
        assertEquals(List.of("discover:biome/plains"), ENGINE.discoverBiome(data, "plains").raised());
        assertFalse(ENGINE.discoverBiome(data, "plains").counted());
        assertEquals(1, data.count("discover:biome/plains"));
        ENGINE.discoverBiome(data, "terralith:alpine_grove");
        assertEquals(1, data.count("discover:biome/terralith:alpine_grove"));
    }

    @Test
    void eachStructureInstanceCountsOnceForEachPlayer() {
        ProgressionData tom = new ProgressionData();
        String city = null;
        assertTrue(StructureVisitors.firstVisit(city, TOM));
        assertTrue(ENGINE.discoverStructure(tom, "ancient_city", StructureVisitors.firstVisit(city, TOM)).counted());
        city = StructureVisitors.with(city, TOM);

        // Walking back into the same city does not count again.
        assertFalse(ENGINE.discoverStructure(tom, "ancient_city", StructureVisitors.firstVisit(city, TOM)).counted());
        assertEquals(1, tom.count("discover:structure/ancient_city"));

        // A friend entering it still counts for them.
        assertTrue(StructureVisitors.firstVisit(city, SAM));
        city = StructureVisitors.with(city, SAM);
        assertFalse(StructureVisitors.firstVisit(city, SAM));
        assertFalse(StructureVisitors.firstVisit(city, TOM));

        // A second city is a new discovery.
        ENGINE.discoverStructure(tom, "ancient_city", StructureVisitors.firstVisit(null, TOM));
        assertEquals(2, tom.count("discover:structure/ancient_city"));
    }
}
