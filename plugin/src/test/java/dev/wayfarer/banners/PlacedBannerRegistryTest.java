package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wayfarer.banners.PlacedBannerRegistry.BlockPos;
import dev.wayfarer.banners.PlacedBannerRegistry.ChunkRef;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlacedBannerRegistryTest {
    private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void negativeCoordinatesMapToTheRightChunk() {
        assertEquals(new ChunkRef(WORLD, -1, 0), new BlockPos(-1, 64, 15).chunk(WORLD));
        assertEquals(new ChunkRef(WORLD, -2, -1), new BlockPos(-17, 64, -16).chunk(WORLD));
    }

    @Test
    void chunkTextRoundTripsThroughUnloadAndLoad() {
        PlacedBannerRegistry registry = new PlacedBannerRegistry();
        BlockPos a = new BlockPos(3, 70, -5);
        BlockPos b = new BlockPos(4, -10, -6);
        registry.add(WORLD, a, "zombie");
        registry.add(WORLD, b, "old banner id");
        ChunkRef chunk = a.chunk(WORLD);
        String text = registry.encode(chunk);

        registry.unload(chunk);
        assertEquals(Optional.empty(), registry.at(WORLD, a));

        PlacedBannerRegistry.Decoded decoded = PlacedBannerRegistry.decode(text);
        assertEquals(List.of(), decoded.problems());
        registry.load(chunk, decoded.banners());
        assertEquals(Optional.of("zombie"), registry.at(WORLD, a));
        assertEquals(Optional.of("old banner id"), registry.at(WORLD, b));
    }

    @Test
    void removingTheLastBannerLeavesNoChunkText() {
        PlacedBannerRegistry registry = new PlacedBannerRegistry();
        BlockPos a = new BlockPos(0, 64, 0);
        registry.add(WORLD, a, "zombie");
        assertEquals(Optional.of("zombie"), registry.remove(WORLD, a));
        assertEquals(Optional.empty(), registry.remove(WORLD, a));
        assertNull(registry.encode(a.chunk(WORLD)));
        assertEquals(0, registry.chunkCount());
    }

    @Test
    void badLinesAreSkippedAndUnknownFormatIsLeftAlone() {
        PlacedBannerRegistry.Decoded decoded = PlacedBannerRegistry.decode("v1\n1 2 3 zombie\nnot a line\n4 5 6");
        assertEquals(Map.of(new BlockPos(1, 2, 3), "zombie"), decoded.banners());
        assertEquals(2, decoded.problems().size());
        PlacedBannerRegistry.Decoded future = PlacedBannerRegistry.decode("v2\n1 2 3 zombie");
        assertTrue(future.banners().isEmpty());
        assertEquals(1, future.problems().size());
    }
}
