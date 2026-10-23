package dev.wayfarer.banners;

/** What the server knows by name; lets the config rules stay free of server types. */
public interface Vocabulary {
    boolean isMob(String name);

    boolean isPattern(String name);

    boolean isColour(String name);

    /** An upper-case spawn reason name such as SPAWNER. */
    boolean isSpawnReason(String name);

    /** An upper-case particle name such as HAPPY_VILLAGER. */
    boolean isParticle(String name);

    /** A lower-case potion effect key such as luck. */
    boolean isEffect(String name);
}
