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
    private volatile List<Ladder> withPrefixes = List.of();

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
        List<Ladder> prefixed = new ArrayList<>();
        for (Ladder ladder : list) {
            ladder.selector().stream().filter(a -> !Ladder.isPrefix(a))
                    .forEach(a -> index.computeIfAbsent(a, k -> new ArrayList<>()).add(ladder));
            if (ladder.selector().stream().anyMatch(Ladder::isPrefix)) {
                prefixed.add(ladder);
            }
        }
        all = List.copyOf(list);
        byActivity = index;
        withPrefixes = List.copyOf(prefixed);
    }

    public List<Ladder> all() {
        return all;
    }

    /** Ladders reading this activity, exactly or through a prefix entry. */
    public List<Ladder> reading(String activity) {
        List<Ladder> exact = byActivity.getOrDefault(activity, List.of());
        List<Ladder> prefixed = withPrefixes;
        if (prefixed.isEmpty()) {
            return exact;
        }
        List<Ladder> out = new ArrayList<>(exact);
        for (Ladder ladder : prefixed) {
            if (!out.contains(ladder) && ladder.reads(activity)) {
                out.add(ladder);
            }
        }
        return out;
    }

    public Optional<Ladder> find(String id) {
        return all.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    /** Ladders registered by one owner, in order. */
    public synchronized List<Ladder> of(String owner) {
        return byOwner.getOrDefault(owner, List.of());
    }
}
