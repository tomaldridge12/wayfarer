package dev.wayfarer.objectives;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.Trackable;
import dev.wayfarer.core.Trackables;
import dev.wayfarer.core.progression.Progression;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Lets the tracker watch a player's current dailies ({@code daily:<id>}) and weekly ({@code weekly:<id>}). Main thread. */
final class ObjectiveTrackables {
    private final Core core;
    private final ObjectivesService service;
    private final Supplier<ObjectivesService.State> state;
    private final Supplier<java.util.Map<String, Feat>> feats;
    private final ServerGoals goals;

    ObjectiveTrackables(Core core, ObjectivesService service, Supplier<ObjectivesService.State> state,
            Supplier<java.util.Map<String, Feat>> feats, ServerGoals goals) {
        this.feats = feats;
        this.goals = goals;
        this.core = core;
        this.service = service;
        this.state = state;
    }

    void register(String owner) {
        Trackables trackables = core.trackables();
        trackables.register(owner, "daily", (player, id) -> view(player, "daily", id));
        trackables.register(owner, "weekly", (player, id) -> view(player, "weekly", id));
        trackables.register(owner, "feat", this::feat);
        trackables.register(owner, "goal", (player, id) -> goals.trackable(id));
    }

    static String idOf(String tier, String objectiveId) {
        return tier + ":" + objectiveId;
    }

    /** A feat the player can see and has not earned; hidden feats stay untrackable until earned (and then they are done). */
    private Optional<Trackable> feat(UUID player, String id) {
        Feat f = feats.get().get(id);
        Optional<Progression.Snapshot> snap = core.progression().snapshot(player);
        if (f == null || snap.isEmpty()) {
            return Optional.empty();
        }
        FeatRules.Status status = FeatRules.status(f, snap.get().counters(), snap.get().earned());
        if (!status.shown(f)) {
            return Optional.empty();
        }
        return Optional.of(new Trackable("feat:" + id, f.name(), status.earned() ? f.at() : Math.min(status.progress(), f.at()), f.at()));
    }

    private Optional<Trackable> view(UUID player, String tier, String objectiveId) {
        ObjectivesPlayerData data = service.store().cached(player);
        Optional<Progression.Snapshot> snap = core.progression().snapshot(player);
        if (data == null || snap.isEmpty()) {
            return Optional.empty();
        }
        ObjectivesService.State s = state.get();
        boolean weekly = tier.equals("weekly");
        ObjectivesPlayerData.Slot slot = weekly ? data.weekly
                : data.dailies.stream().filter(x -> x.id.equals(objectiveId)).findFirst().orElse(null);
        ObjectiveDefinition d = (weekly ? s.weekly() : s.daily()).get(objectiveId);
        if (slot == null || d == null || !slot.id.equals(objectiveId)) {
            return Optional.empty();
        }
        long progress = slot.done ? d.target() : Math.min(d.target(), ObjectiveRules.progress(d, snap.get().counters(), slot.baseline));
        return Optional.of(new Trackable(idOf(tier, objectiveId), d.task(), progress, d.target()));
    }
}
