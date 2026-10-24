package dev.wayfarer.core.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Every module's registered ladders, in registration order, indexed by the activities they read. */
public final class Ladders {
    private final Map<String, List<Ladder>> byOwner = new LinkedHashMap<>();
    private volatile List<Ladder> all = List.of();
    private volatile Map<String, List<Ladder>> byActivity = Map.of();

    /** Replaces everything this owner registered; throws if a ladder id is taken by another owner. */
    public synchronized void set(String owner, List<Ladder> ladders) {
        for (Ladder ladder : ladders) {
            byOwner.forEach((other, theirs) -> {
                if (!other.equals(owner) && theirs.stream().anyMatch(l -> l.id().equals(ladder.id()))) {
                    throw new IllegalArgumentException("Ladder " + ladder.id() + " is already registered by " + other);
                }
            });
        }
        byOwner.put(owner, List.copyOf(ladders));
        rebuild();
    }

    public synchronized void drop(String owner) {
        byOwner.remove(owner);
        rebuild();
    }

    private void rebuild() {
        List<Ladder> list = new ArrayList<>();
        byOwner.values().forEach(list::addAll);
        Map<String, List<Ladder>> index = new LinkedHashMap<>();
        for (Ladder ladder : list) {
            ladder.selector().forEach(a -> index.computeIfAbsent(a, k -> new ArrayList<>()).add(ladder));
        }
        all = List.copyOf(list);
        byActivity = index;
    }

    public List<Ladder> all() {
        return all;
    }

    public List<Ladder> reading(String activity) {
        return byActivity.getOrDefault(activity, List.of());
    }

    public Optional<Ladder> find(String id) {
        return all.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    /** Ladders registered by one owner, in order. */
    public synchronized List<Ladder> of(String owner) {
        return byOwner.getOrDefault(owner, List.of());
    }
}
