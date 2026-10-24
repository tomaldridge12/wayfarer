package dev.wayfarer.core.progression;

import java.util.List;

/**
 * The progression rules: given a player's record and something they did, decides whether it counted and raises
 * their counters. No server types; mutates only the record it is given.
 */
public final class ProgressionEngine {
    /** What one activity did: whether it counted and which counters went up. */
    public record Outcome(boolean counted, List<String> raised) {
        static final Outcome NOT_COUNTED = new Outcome(false, List.of());
    }

    private final ProgressionSettings settings;

    public ProgressionEngine(ProgressionSettings settings) {
        this.settings = settings;
    }

    public ProgressionSettings settings() {
        return settings;
    }

    /** A mob death credited to its killer; bosses and minions are counted elsewhere, so never pass them here. */
    public Outcome kill(ProgressionData data, KillFacts kill) {
        if (!settings.credit().counts(kill)) {
            return Outcome.NOT_COUNTED;
        }
        return record(data, Activity.KILL + ":" + kill.mobType());
    }

    /** One more of an activity that has no credit rules of its own. */
    public Outcome record(ProgressionData data, String activity) {
        data.set(activity, data.count(activity) + 1);
        return new Outcome(true, List.of(activity));
    }

    /** Sets a counter (for operators); never below zero. */
    public Outcome set(ProgressionData data, String activity, long count) {
        data.set(activity, Math.max(0, count));
        return new Outcome(true, List.of(activity));
    }
}
