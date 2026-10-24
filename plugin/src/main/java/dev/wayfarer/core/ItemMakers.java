package dev.wayfarer.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Items one module makes for others, by key: the alloys module registers {@code alloy}, and banners asks for
 * {@code alloy:steel_axe} to build a relic on it. A missing maker or id gives empty, so callers fall back.
 */
public final class ItemMakers<T> {
    private record Maker<T>(String owner, Function<String, Optional<T>> make) {
    }

    private final Map<String, Maker<T>> byPrefix = new LinkedHashMap<>();

    /** Registers a prefix; throws if another owner already has it. */
    public void register(String owner, String prefix, Function<String, Optional<T>> make) {
        Maker<T> old = byPrefix.get(prefix);
        if (old != null && !old.owner().equals(owner)) {
            throw new IllegalArgumentException("Item prefix '" + prefix + "' is already registered by " + old.owner());
        }
        byPrefix.put(prefix, new Maker<>(owner, make));
    }

    /** The item for {@code prefix:id}, or empty when no module makes it. */
    public Optional<T> make(String key) {
        int cut = key.indexOf(':');
        if (cut <= 0) {
            return Optional.empty();
        }
        Maker<T> maker = byPrefix.get(key.substring(0, cut));
        return maker == null ? Optional.empty() : maker.make().apply(key.substring(cut + 1));
    }

    /** Forgets the owner's makers, for a module that failed to start or was disabled. */
    public void drop(String owner) {
        byPrefix.values().removeIf(m -> m.owner().equals(owner));
    }
}
