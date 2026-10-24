package dev.wayfarer.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Wayfarer ids of custom items, so activities can tell them apart: a fish is {@code catch:wayfarer:fish/arapaima}, not
 * {@code catch:cod}. Items carry {@code <kind>/<id>} (see {@link ItemTagService#writeId}); a module owns each kind
 * and says which ids it knows. Main thread.
 */
public final class ItemIds {
    /** The namespace resolved items are counted under. */
    public static final String NAMESPACE = "wayfarer";

    private record Kind(String owner, Supplier<Collection<String>> ids) {
        boolean knows(String id) {
            return ids.get().contains(id);
        }
    }

    private final Map<String, Kind> kinds = new LinkedHashMap<>();

    /**
     * Claims a kind ({@code fish}) with the ids it currently has (asked afresh each time, so reloads show); throws if
     * another owner already has it.
     */
    public void register(String owner, String kind, Supplier<Collection<String>> ids) {
        Kind old = kinds.get(kind);
        if (old != null && !old.owner().equals(owner)) {
            throw new IllegalArgumentException("Item kind '" + kind + "' is already registered by " + old.owner());
        }
        kinds.put(kind, new Kind(owner, ids));
    }

    /** Every id of a kind, for collections that list them all; empty when no running module owns the kind. */
    public List<String> ids(String kind) {
        Kind k = kinds.get(kind);
        return k == null ? List.of() : k.ids().get().stream().sorted().toList();
    }

    /** Forgets the owner's kinds, for a module that failed to start or was disabled. */
    public void drop(String owner) {
        kinds.values().removeIf(k -> k.owner().equals(owner));
    }

    /**
     * The activity target for an item's id ({@code fish/arapaima} gives {@code wayfarer:fish/arapaima}); empty when no
     * running module owns the kind or knows the id, and then the item counts as nothing at all.
     */
    public Optional<String> target(String itemId) {
        int cut = itemId.indexOf('/');
        Kind kind = cut <= 0 ? null : kinds.get(itemId.substring(0, cut));
        return kind != null && kind.knows(itemId.substring(cut + 1))
                ? Optional.of(NAMESPACE + ":" + itemId) : Optional.empty();
    }
}
