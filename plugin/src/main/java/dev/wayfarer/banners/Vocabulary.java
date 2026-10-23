package dev.wayfarer.banners;

/** What the server knows by name; lets the config rules stay free of server types. */
public interface Vocabulary {
    boolean isMob(String name);

    boolean isPattern(String name);

    boolean isColour(String name);
}
