package dev.wayfarer.banners;

/** A trophy head on a family's ladder. */
public record HeadDefinition(String id, String name, String family, int kills, String item, String skin) {
    public HeadDefinition(String id, String name, String family, int kills) {
        this(id, name, family, kills, null, null);
    }
}
