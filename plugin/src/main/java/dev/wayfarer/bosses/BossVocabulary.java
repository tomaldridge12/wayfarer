package dev.wayfarer.bosses;

/** What the server knows by name, so boss config rules stay free of server types. */
public interface BossVocabulary {
    /** A living, spawnable entity type such as zombie. */
    boolean isBossEntity(String name);

    /** An item such as iron_helmet. */
    boolean isItem(String name);

    /** A named text colour such as dark_green, for the glow. */
    boolean isGlowColour(String name);

    /** A biome key such as plains or the_end. */
    default boolean isBiome(String name) {
        return true;
    }

    /** A structure key such as desert_pyramid. */
    default boolean isStructure(String name) {
        return true;
    }
}
