package dev.wayfarer.core.progression;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * The progression rules: given a player's record and something they did, decides whether it counted, raises
 * their counters and awards the ladder rungs they newly reached. No server types; mutates only the record it
 * is given.
 */
public final class ProgressionEngine {
    /** A ladder reward newly earned. */
    public record Earned(Ladder ladder, String reward) {
    }

    /** What one activity did: whether it counted, which counters went up, and rewards newly earned in order. */
    public record Outcome(boolean counted, List<String> raised, List<Earned> earned) {
        static final Outcome NOT_COUNTED = new Outcome(false, List.of(), List.of());
    }

    public enum ClaimResult {
        OK, NOT_EARNED, COOLDOWN
    }

    /** How long a player waits between claims of the same reward. */
    public static final long CLAIM_COOLDOWN_MILLIS = 24L * 60 * 60 * 1000;

    private final ProgressionSettings settings;
    private final Ladders ladders;
    private final LongSupplier clock;

    public ProgressionEngine(ProgressionSettings settings, Ladders ladders, LongSupplier clock) {
        this.settings = settings;
        this.ladders = ladders;
        this.clock = clock;
    }

    public ProgressionEngine(ProgressionSettings settings) {
        this(settings, new Ladders(), System::currentTimeMillis);
    }

    public ProgressionSettings settings() {
        return settings;
    }

    public Ladders ladders() {
        return ladders;
    }

    /** A mob death credited to its killer; bosses and minions are counted from their defeat, so never pass them. */
    public Outcome kill(ProgressionData data, KillFacts kill) {
        if (!settings.credit().counts(kill)) {
            return Outcome.NOT_COUNTED;
        }
        return record(data, Activity.KILL + ":" + kill.mobType());
    }

    /** One more of an activity that has no credit rules of its own. */
    public Outcome record(ProgressionData data, String activity) {
        return record(data, List.of(activity));
    }

    /** One more of each activity, as one outcome (a ripe crop is both mine: and harvest:). */
    public Outcome record(ProgressionData data, List<String> activities) {
        for (String activity : activities) {
            data.set(activity, data.count(activity) + 1);
        }
        return new Outcome(true, List.copyOf(activities), climb(data, activities));
    }

    /** Sets a counter (for operators); never below zero. Awards what the new count reaches and never revokes. */
    public Outcome set(ProgressionData data, String activity, long count) {
        data.set(activity, Math.max(0, count));
        return new Outcome(true, List.of(activity), climb(data, List.of(activity)));
    }

    /** Earns a reward outside a ladder (a drop); false if the player already had it. */
    public boolean award(ProgressionData data, String reward) {
        return data.markEarned(reward, clock.getAsLong());
    }

    /** The player's position on a ladder. */
    public long progress(ProgressionData data, Ladder ladder) {
        return ladder.progress(data);
    }

    /** Whether the player may claim a copy of an earned reward now; on OK, records the claim time. */
    public ClaimResult claim(ProgressionData data, String reward) {
        if (!data.isEarned(reward)) {
            return ClaimResult.NOT_EARNED;
        }
        long now = clock.getAsLong();
        if (now - data.lastClaim(reward) < CLAIM_COOLDOWN_MILLIS) {
            return ClaimResult.COOLDOWN;
        }
        data.setLastClaim(reward, now);
        return ClaimResult.OK;
    }

    /** Milliseconds until the reward can be claimed again; 0 when it can be now. */
    public long claimWait(ProgressionData data, String reward) {
        return Math.max(0, data.lastClaim(reward) + CLAIM_COOLDOWN_MILLIS - clock.getAsLong());
    }

    /** Awards every rung now reached on the ladders reading these activities, ladder by ladder, rung by rung. */
    private List<Earned> climb(ProgressionData data, List<String> activities) {
        Set<Ladder> touched = new LinkedHashSet<>();
        activities.forEach(a -> touched.addAll(ladders.reading(a)));
        List<Earned> earned = new ArrayList<>();
        long now = clock.getAsLong();
        for (Ladder ladder : touched) {
            long progress = ladder.progress(data);
            for (Ladder.Rung rung : ladder.rungs()) {
                if (rung.threshold() != Ladder.Rung.NEVER && progress >= rung.threshold()
                        && data.markEarned(rung.reward(), now)) {
                    earned.add(new Earned(ladder, rung.reward()));
                }
            }
        }
        return earned;
    }
}
