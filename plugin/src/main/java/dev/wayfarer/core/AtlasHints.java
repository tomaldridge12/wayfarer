package dev.wayfarer.core;

/** Offered by the collections module: the hint the Atlas shows for a biome the player has not found yet. */
public interface AtlasHints {
    /**
     * The hint for a biome key ({@code plains}, {@code minecraft:plains} or {@code terralith:alpine_grove}); empty for a
     * biome the Atlas does not list.
     */
    String hint(String biome);
}
