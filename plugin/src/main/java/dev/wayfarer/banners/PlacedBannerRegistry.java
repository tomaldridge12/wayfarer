package dev.wayfarer.banners;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Placed banners in loaded chunks, keyed by block position. Free of server types: the glue loads a chunk's
 * encoded text when the chunk loads and writes it back whenever the chunk's banners change.
 */
public final class PlacedBannerRegistry {
    static final String FORMAT = "v1";

    public record ChunkRef(UUID world, int x, int z) {
    }

    public record BlockPos(int x, int y, int z) {
        public ChunkRef chunk(UUID world) {
            return new ChunkRef(world, x >> 4, z >> 4);
        }
    }

    public record Placed(UUID world, BlockPos pos, String bannerId) {
    }

    /** Positions and ids decoded from one chunk's text, and any lines that could not be read. */
    public record Decoded(Map<BlockPos, String> banners, List<String> problems) {
    }

    private final Map<ChunkRef, Map<BlockPos, String>> chunks = new HashMap<>();

    public void load(ChunkRef chunk, Map<BlockPos, String> banners) {
        if (banners.isEmpty()) {
            chunks.remove(chunk);
        } else {
            chunks.put(chunk, new LinkedHashMap<>(banners));
        }
    }

    public void unload(ChunkRef chunk) {
        chunks.remove(chunk);
    }

    public void clear() {
        chunks.clear();
    }

    public void add(UUID world, BlockPos pos, String bannerId) {
        chunks.computeIfAbsent(pos.chunk(world), c -> new LinkedHashMap<>()).put(pos, bannerId);
    }

    public Optional<String> at(UUID world, BlockPos pos) {
        Map<BlockPos, String> inChunk = chunks.get(pos.chunk(world));
        return inChunk == null ? Optional.empty() : Optional.ofNullable(inChunk.get(pos));
    }

    public Optional<String> remove(UUID world, BlockPos pos) {
        ChunkRef chunk = pos.chunk(world);
        Map<BlockPos, String> inChunk = chunks.get(chunk);
        if (inChunk == null) {
            return Optional.empty();
        }
        String id = inChunk.remove(pos);
        if (inChunk.isEmpty()) {
            chunks.remove(chunk);
        }
        return Optional.ofNullable(id);
    }

    public Stream<Placed> all() {
        return chunks.entrySet().stream().flatMap(c -> c.getValue().entrySet().stream()
                .map(b -> new Placed(c.getKey().world(), b.getKey(), b.getValue())));
    }

    /** Placed banners in loaded chunks overlapping the square of this radius around (x, z). */
    public Stream<Placed> within(UUID world, double x, double z, int radius) {
        int minX = (int) Math.floor(x - radius) >> 4;
        int maxX = (int) Math.floor(x + radius) >> 4;
        int minZ = (int) Math.floor(z - radius) >> 4;
        int maxZ = (int) Math.floor(z + radius) >> 4;
        Stream.Builder<Placed> out = Stream.builder();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                Map<BlockPos, String> inChunk = chunks.get(new ChunkRef(world, cx, cz));
                if (inChunk != null) {
                    inChunk.forEach((pos, id) -> out.add(new Placed(world, pos, id)));
                }
            }
        }
        return out.build();
    }

    public int chunkCount() {
        return chunks.size();
    }

    /** The chunk's text to store, or null when it has no placed banners. */
    public String encode(ChunkRef chunk) {
        Map<BlockPos, String> inChunk = chunks.get(chunk);
        if (inChunk == null || inChunk.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder(FORMAT);
        inChunk.forEach((pos, id) -> out.append('\n')
                .append(pos.x()).append(' ').append(pos.y()).append(' ').append(pos.z()).append(' ').append(id));
        return out.toString();
    }

    /** Reads text written by {@link #encode}; bad lines are reported and skipped. */
    public static Decoded decode(String text) {
        Map<BlockPos, String> banners = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        String[] lines = text.split("\n");
        if (!FORMAT.equals(lines[0])) {
            problems.add("unknown placed-banner format '" + lines[0] + "'; chunk data left untouched");
            return new Decoded(Map.of(), problems);
        }
        for (int i = 1; i < lines.length; i++) {
            String[] parts = lines[i].split(" ", 4);
            try {
                if (parts.length != 4 || parts[3].isBlank()) {
                    throw new NumberFormatException();
                }
                banners.put(new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2])), parts[3]);
            } catch (NumberFormatException e) {
                problems.add("unreadable placed-banner line '" + lines[i] + "' skipped");
            }
        }
        return new Decoded(banners, problems);
    }
}
