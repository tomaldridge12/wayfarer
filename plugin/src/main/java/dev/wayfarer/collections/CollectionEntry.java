package dev.wayfarer.collections;

/**
 * One thing a collection asks you to find: the activity that counts it (a group {@code a|b} for variants), its name and
 * icon (a vanilla item key, or a Wayfarer item key such as {@code curio:moss_heart}), the hint shown until it is found,
 * the tab it sits on (a dimension, a kind of collectible; empty for none) and how its count reads once found.
 */
public record CollectionEntry(String key, String activity, String name, String icon, String hint, String tab,
        String verb) {
}
