package dev.wayfarer.bosses;

/** What the server knows by name, so boss config rules stay free of server types. */
public interface BossVocabulary {
    /** A living, spawnable entity type such as zombie. */
    boolean isBossEntity(String name);

    /** An item such as iron_helmet. */
    boolean isItem(String name);

    /** A named text colour such as dark_green, for the glow. */
    boolean isGlowColour(String name);

    /** A full biome key such as minecraft:plains or terralith:desert_canyon. */
    default boolean isBiome(String key) {
        return true;
    }

    /**
     * The full keys of every biome in a biome tag such as minecraft:is_forest, or null when the server has no such
     * tag (common with c: tags that only datapacks like Terralith define).
     */
    default java.util.Set<String> biomeTag(String key) {
        return null;
    }

    /** A full structure key such as minecraft:desert_pyramid. */
    default boolean isStructure(String key) {
        return true;
    }
}
