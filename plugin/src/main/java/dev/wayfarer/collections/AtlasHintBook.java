package dev.wayfarer.collections;

import dev.wayfarer.core.AtlasHints;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Atlas's biome hints by biome key: what the config says, else the generated one. No server types. */
final class AtlasHintBook implements AtlasHints {
    private static final String PREFIX = "biome/";

    private final Map<String, String> hints = new HashMap<>();

    /** Reads the hints from the Atlas's entries ({@code biome/<target>}); other entries are ignored. */
    AtlasHintBook(List<CollectionEntry> atlas) {
        for (CollectionEntry entry : atlas) {
            if (entry.key().startsWith(PREFIX)) {
                hints.put(entry.key().substring(PREFIX.length()), entry.hint());
            }
        }
    }

    @Override
    public String hint(String biome) {
        return hints.getOrDefault(biome.startsWith("minecraft:") ? biome.substring("minecraft:".length()) : biome, "");
    }
}
