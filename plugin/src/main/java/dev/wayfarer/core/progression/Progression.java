package dev.wayfarer.core.progression;

import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** What modules use of core's progression: ladders, reward types, awards and reading progress. Main thread. */
public interface Progression {
    /** Registers a kind of reward under its type ("banner"), so rewards written "banner:zombie" can be made. */
    void registerRewardType(String owner, String type, RewardType rewardType);

    /** Replaces every ladder this owner registered (on enable and on reload). */
    void setLadders(String owner, List<Ladder> ladders);

    /** Forgets the owner's ladders and reward types, for a module that failed to start. */
    void drop(String owner);

    /** Counts one activity for a player, online or not, awarding any rungs it reaches. */
    void record(UUID player, String activity, String how);

    /** Earns a reward outside a ladder (a drop) once, delivering it with the earning moment if they are online. */
    void award(UUID player, String reward, String how, boolean announce);

    /** The player's position on a ladder if their record is loaded; empty otherwise (see {@link #load}). */
    OptionalLong progressIfLoaded(UUID player, String ladderId);

    /** Loads a player's record, for callers that want to redraw once it is ready. */
    CompletableFuture<Void> load(UUID player);
}
