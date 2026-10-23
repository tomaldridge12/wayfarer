package dev.wayfarer.banners;

import java.util.Set;

/** A named set of entity type names that count as one kind of mob. */
public record MobFamily(String id, Set<String> mobs) {
    /** The reserved all-mobs family: every counted kill of a hostile mob (or any mob, if configured). */
    public static final String ALL = "all";
}
