package dev.wayfarer.bosses;

/** What the server knows by name, so boss config rules stay free of server types. */
public interface BossVocabulary {
    /** A living, spawnable entity type such as zombie. */
    boolean isBossEntity(String name);

    /** An item such as iron_helmet. */
    boolean isItem(String name);

    /** A named text colour such as dark_green, for the glow. */
    boolean isGlowColour(String name);
}
