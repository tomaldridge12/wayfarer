package dev.wayfarer.collections;

import java.util.List;
import java.util.Map;

/**
 * One collection from collections.yml: its title, colour and blurb, what one entry is called, its milestones and the
 * hints unfound entries show. {@code settings} keeps the collection's own extra keys (exclude, groups, ...).
 */
public record CollectionDefinition(String id, String title, int colour, String blurb, String unit,
        List<Milestone> milestones, Map<String, String> hints, Map<String, Object> settings) {
    /** A milestone as written: a count ("10") or a share ("25%"), and what it pays. */
    public record Milestone(String at, List<String> rewards) {
        public boolean share() {
            return at.endsWith("%");
        }
    }

    public CollectionDefinition {
        milestones = List.copyOf(milestones);
        hints = Map.copyOf(hints);
        settings = Map.copyOf(settings);
    }

    /** The ladder that counts this collection. */
    public String ladderId() {
        return "collection_" + id;
    }

    /** "creature" or "creatures"; a unit written "fish|fish" names its plural. */
    public String unit(long n) {
        String[] parts = unit.split("\\|");
        return n == 1 ? parts[0] : parts.length > 1 ? parts[1] : parts[0] + "s";
    }
}
