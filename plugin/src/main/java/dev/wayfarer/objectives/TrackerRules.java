package dev.wayfarer.objectives;

import dev.wayfarer.core.Trackable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** The tracked list: a capped, ordered set of tracked ids, and what the sidebar shows from it. No server types. */
public final class TrackerRules {
    public static final int DEFAULT_CAP = 5;

    public enum Result {
        ADDED, ALREADY, FULL
    }

    private TrackerRules() {
    }

    /** Appends the id unless it is already there or the list is at its cap. */
    public static Result add(List<String> tracked, String id, int cap) {
        if (tracked.contains(id)) {
            return Result.ALREADY;
        }
        if (tracked.size() >= cap) {
            return Result.FULL;
        }
        tracked.add(id);
        return Result.ADDED;
    }

    public static boolean remove(List<String> tracked, String id) {
        return tracked.remove(id);
    }

    /**
     * What to show, in tracked order: each id's current view, leaving out finished things and ids nothing knows any
     * more. Those are also dropped from {@code tracked} so they free their place.
     */
    public static List<Trackable> live(List<String> tracked, Function<String, Optional<Trackable>> lookup) {
        List<Trackable> out = new ArrayList<>();
        tracked.removeIf(id -> {
            Optional<Trackable> view = lookup.apply(id);
            if (view.isEmpty() || view.get().done()) {
                return true;
            }
            out.add(view.get());
            return false;
        });
        return out;
    }

    /** "Mine 32 coal 12/32". */
    public static String line(Trackable t) {
        return t.title() + " " + FeatRules.number(t.progress()) + "/" + FeatRules.number(t.target());
    }
}
