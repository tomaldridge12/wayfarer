package dev.wayfarer.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * What one module offers others, by interface: curios answer "how much does this player's carried curio boost
 * treasure luck", exploration "point this player a Wayfinder". The interfaces live in core; a module that is off
 * simply offers nothing, so callers fall back. Main thread.
 */
public final class Services {
    private record Offer(String owner, Object service) {
    }

    private final Map<Class<?>, Offer> byType = new LinkedHashMap<>();

    /** Offers a service; throws if another owner already offers that interface. */
    public <T> void register(String owner, Class<T> type, T service) {
        Offer old = byType.get(type);
        if (old != null && !old.owner().equals(owner)) {
            throw new IllegalArgumentException(type.getSimpleName() + " is already offered by " + old.owner());
        }
        byType.put(type, new Offer(owner, service));
    }

    public <T> Optional<T> get(Class<T> type) {
        Offer offer = byType.get(type);
        return offer == null ? Optional.empty() : Optional.of(type.cast(offer.service()));
    }

    /** Withdraws the owner's services, for a module that failed to start or was disabled. */
    public void drop(String owner) {
        byType.values().removeIf(o -> o.owner().equals(owner));
    }
}
