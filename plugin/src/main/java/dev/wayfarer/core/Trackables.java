package dev.wayfarer.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the tracker can watch. A module registers a source under a prefix ({@code daily}), and a tracked id reads
 * {@code prefix:rest}; the sidebar asks here without knowing who owns what. Main thread.
 */
public final class Trackables {
    /** Looks up one tracked thing for a player; empty when it no longer exists (a daily that turned over). */
    public interface Source {
        Optional<Trackable> view(UUID player, String id);
    }

    private record Owned(String owner, Source source) {
    }

    private final Map<String, Owned> byPrefix = new LinkedHashMap<>();

    /** Registers a source; throws if another owner already uses the prefix. */
    public void register(String owner, String prefix, Source source) {
        Owned old = byPrefix.get(prefix);
        if (old != null && !old.owner().equals(owner)) {
            throw new IllegalArgumentException("Trackable prefix " + prefix + " is already used by " + old.owner());
        }
        byPrefix.put(prefix, new Owned(owner, source));
    }

    /** The tracked thing for {@code prefix:rest}, or empty if no source knows it. */
    public Optional<Trackable> view(UUID player, String trackedId) {
        int colon = trackedId.indexOf(':');
        Owned owned = colon < 0 ? null : byPrefix.get(trackedId.substring(0, colon));
        return owned == null ? Optional.empty() : owned.source().view(player, trackedId.substring(colon + 1));
    }

    /** Withdraws the owner's sources, for a module that failed to start or was disabled. */
    public void drop(String owner) {
        byPrefix.values().removeIf(o -> o.owner().equals(owner));
    }
}
