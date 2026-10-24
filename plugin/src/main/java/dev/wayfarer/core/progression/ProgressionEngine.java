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

    /**
     * What one activity did: whether it counted, which counters went up, rewards newly earned in order, and the
     * items pity adds to this death's drops.
     */
    public record Outcome(boolean counted, List<String> raised, List<Earned> earned, List<String> pityItems) {
        static final Outcome NOT_COUNTED = new Outcome(false, List.of(), List.of(), List.of());

        Outcome(boolean counted, List<String> raised, List<Earned> earned) {
            this(counted, raised, earned, List.of());
        }
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
        return kill(data, kill, null, Set.of());
    }

    /**
     * A mob death credited to the player who landed the killing blow, with what the mob held ({@code null} for
     * nothing) and the items it really dropped. Each pity drop for that mob advances on a counted kill, resets when
     * its item really dropped, and when it reaches its threshold is added to the drops and resets.
     */
    public Outcome kill(ProgressionData data, KillFacts kill, String heldItem, Set<String> droppedItems) {
        if (!settings.credit().counts(kill)) {
            return Outcome.NOT_COUNTED;
        }
        Outcome counted = record(data, Activity.KILL + ":" + kill.mobType());
        List<String> pityItems = new ArrayList<>();
        for (PityDrop pity : settings.pity()) {
            if (!pity.applies(kill.mobType(), heldItem)) {
                continue;
            }
            int misses = droppedItems.contains(pity.item()) ? 0 : data.pity(pity.id()) + 1;
            if (misses >= pity.threshold()) {
                pityItems.add(pity.item());
                misses = 0;
            }
            data.setPity(pity.id(), misses);
        }
        return new Outcome(true, counted.raised(), counted.earned(), List.copyOf(pityItems));
    }

    /**
     * A block broken directly by a player ({@code block} is an activity target). A crop broken fully grown
     * ({@code grown}: at its maximum age, or a melon or pumpkin) is also a harvest.
     */
    public Outcome mine(ProgressionData data, String block, boolean grown) {
        String mine = Activity.MINE + ":" + block;
        return grown && Activity.CROPS.contains(block)
                ? record(data, List.of(mine, Activity.HARVEST + ":" + block))
                : record(data, mine);
    }

    /** The first time a player stands in a biome; it counts once and stays at 1. */
    public Outcome discoverBiome(ProgressionData data, String biome) {
        String activity = Activity.DISCOVER + ":biome/" + biome;
        return data.count(activity) > 0 ? Outcome.NOT_COUNTED : record(data, activity);
    }

    /** Entering one structure; counts only on the player's first visit to that structure instance. */
    public Outcome discoverStructure(ProgressionData data, String structure, boolean firstVisit) {
        return firstVisit ? record(data, Activity.DISCOVER + ":structure/" + structure) : Outcome.NOT_COUNTED;
    }

    /** Items a player crafted: {@code craft:<item>} goes up by the amount made. */
    public Outcome craft(ProgressionData data, String item, int amount) {
        if (amount <= 0) {
            return Outcome.NOT_COUNTED;
        }
        String activity = Activity.CRAFT + ":" + item;
        data.set(activity, data.count(activity) + amount);
        return new Outcome(true, List.of(activity), climb(data, List.of(activity)));
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
