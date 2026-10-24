package dev.wayfarer.objectives;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.progression.ProgressChangedEvent;
import dev.wayfarer.core.progression.Progression;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The weekly server goal: drawn at the stage most players have reached, everyone's contribution counted from when they
 * were first seen that week, announced at each quarter, and on completion a keepsake and a goal-tier roll for every
 * contributor (kept for them if they are away). The state is caught up after downtime. Main thread.
 */
final class ServerGoals implements Listener {
    static final int BLUE = 0x5B9BD5;
    private static final long SEEN_DAYS = 14;
    static final DateTimeFormatter WEEK = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).withZone(ZoneId.of("Europe/London"));

    private final JavaPlugin plugin;
    private final Core core;
    private final Supplier<ObjectivesService.State> pools;
    private final Supplier<Map<String, ObjectiveDefinition>> goalPool;
    private final ObjectivesService service;
    private final Loot loot;
    private final dev.wayfarer.core.StateFile file;
    private ServerGoalState state;
    private boolean dirty;
    private BukkitTask tick;

    ServerGoals(JavaPlugin plugin, Core core, Supplier<ObjectivesService.State> pools, Supplier<Map<String, ObjectiveDefinition>> goalPool,
            ObjectivesService service, Loot loot) {
        this.plugin = plugin;
        this.core = core;
        this.pools = pools;
        this.goalPool = goalPool;
        this.service = service;
        this.loot = loot;
        this.file = new dev.wayfarer.core.StateFile(plugin.getDataFolder().toPath().resolve("data").resolve("server-goal.json"), core.logger());
    }

    ServerGoalState state() {
        return state;
    }

    void markDirty() {
        dirty = true;
    }

    void start() {
        state = ServerGoalState.read(file.path(), core.logger());
        rollover(false);
        plugin.getServer().getOnlinePlayers().forEach(this::seen);
        tick = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            rollover(true);
            save(false);
        }, 1200, 1200);
        ObjectiveDefinition g = goal();
        core.logger().info("Server goal: " + (g == null ? "none" : g.id() + " " + ServerGoalRules.total(state.contributions) + " / "
                + g.target() + " (" + state.contributions.size() + " contributors" + (state.completed ? ", completed" : "") + ")"));
    }

    void stop() {
        if (tick != null) {
            tick.cancel();
        }
        if (state != null) {
            file.close(state.json());
        }
    }

    /** Queues a write of the state (taken here, written in order off the main thread); {@code now} even if unchanged. */
    void save(boolean now) {
        if (!dirty && !now) {
            return;
        }
        dirty = false;
        file.save(state.json());
    }

    ObjectiveDefinition goal() {
        return state == null || state.goal == null ? null : goalPool.get().get(state.goal);
    }

    /** A new week means a new goal; the old one simply ends. {@code live}: announce it (not while catching up at start). */
    void rollover(boolean live) {
        long week = pools.get().settings().schedule().weeklyStart(Instant.now()).toEpochMilli();
        if (state.weekStart == week && goal() != null) {
            return;
        }
        ObjectiveDefinition old = goal();
        if (old != null && !state.completed) {
            core.logger().info("[objectives] Server goal " + old.id() + " ended unfinished at " + ServerGoalRules.total(state.contributions)
                    + " / " + old.target());
        }
        long cutoff = System.currentTimeMillis() - SEEN_DAYS * 24 * 3600 * 1000;
        List<String> stages = new ArrayList<>();
        state.playerStages.forEach((id, stage) -> {
            if (state.lastSeen.getOrDefault(id, 0L) >= cutoff) {
                stages.add(stage);
            }
        });
        String stage = ServerGoalRules.commonStage(stages, pools.get().settings().stages());
        ObjectiveDefinition next = ServerGoalRules.choose(goalPool.get().values(), pools.get().settings().stages(), stage,
                ThreadLocalRandom.current());
        state.weekStart = week;
        state.goal = next == null ? null : next.id();
        state.stage = stage;
        state.baselines.clear();
        state.contributions.clear();
        state.paid.clear();
        state.completed = false;
        dirty = true;
        if (next == null) {
            return;
        }
        core.logger().info("[objectives] Server goal for the week of " + WEEK.format(Instant.ofEpochMilli(week)) + ": " + next.id()
                + " at stage " + stage);
        plugin.getServer().getOnlinePlayers().forEach(this::seen);
        if (live) {
            plugin.getServer().broadcast(ItemLook.text("✦ This week's server goal: ", BLUE).append(ItemLook.text(next.name(), ObjectivesService.PAPER))
                    .append(ItemLook.text(" · " + next.task() + " together", ObjectivesService.INK)));
        }
    }

    /** A player is seen this week: their stage is remembered, and their count when first seen is the baseline. */
    void seen(Player player) {
        String id = player.getUniqueId().toString();
        state.playerStages.put(id, service.stage(player));
        state.lastSeen.put(id, System.currentTimeMillis());
        ObjectiveDefinition g = goal();
        Optional<Progression.Snapshot> snap = core.progression().snapshot(player.getUniqueId());
        if (g != null && snap.isPresent() && !state.baselines.containsKey(id)) {
            state.baselines.put(id, ObjectiveRules.value(g, snap.get().counters()));
        }
        dirty = true;
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                seen(player);
            }
        }, 40);
    }

    @EventHandler
    void onProgress(ProgressChangedEvent event) {
        ObjectiveDefinition g = goal();
        if (g == null || state.completed) {
            return;
        }
        String id = event.player().toString();
        Long baseline = state.baselines.get(id);
        Optional<Progression.Snapshot> snap = core.progression().snapshot(event.player());
        if (baseline == null || snap.isEmpty()) {
            return;
        }
        long now = Math.max(0, ObjectiveRules.value(g, snap.get().counters()) - baseline);
        if (now != state.contributions.getOrDefault(id, 0L)) {
            contribute(id, now);
        }
    }

    /** Sets a player's contribution and acts on what the new total reaches. */
    void contribute(String id, long contribution) {
        ObjectiveDefinition g = goal();
        if (g == null || state.completed) {
            return;
        }
        long before = ServerGoalRules.total(state.contributions);
        state.contributions.put(id, contribution);
        long after = ServerGoalRules.total(state.contributions);
        dirty = true;
        for (int q : ServerGoalRules.quarters(before, after, g.target())) {
            plugin.getServer().broadcast(ItemLook.text("✦ The server goal is " + q + "% done: ", BLUE)
                    .append(ItemLook.text(g.name() + " · " + FeatRules.number(after) + " / " + FeatRules.number(g.target()), ObjectivesService.PAPER)));
        }
        if (after >= g.target()) {
            complete(g);
        }
    }

    /** Everyone who helped gets a keepsake and a goal-tier roll, now or when they next join. */
    void complete(ObjectiveDefinition g) {
        state.completed = true;
        dirty = true;
        long total = ServerGoalRules.total(state.contributions);
        plugin.getServer().broadcast(ItemLook.text("✦ The server goal is complete! ", BLUE).append(ItemLook.text(g.name()
                + " · " + FeatRules.number(total) + " together. Everyone who helped has a keepsake.", ObjectivesService.PAPER)));
        plugin.getServer().getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f));
        core.logger().info("[objectives] Server goal " + g.id() + " completed at " + total + " by " + state.contributions.size() + " players");
        for (Map.Entry<String, Long> e : new ArrayList<>(state.contributions.entrySet())) {
            if (e.getValue() <= 0 || state.paid.contains(e.getKey())) {
                continue;
            }
            state.paid.add(e.getKey());
            UUID uuid = UUID.fromString(e.getKey());
            long contribution = e.getValue();
            service.with(uuid, (data, snap) -> {
                List<String> paid = new ArrayList<>();
                paid.add(Keepsakes.key(g.id(), state.weekStart, contribution, g.target()));
                paid.addAll(pools.get().tables().roll(g.table(), "goal", r -> service.owns(uuid, data, r), ThreadLocalRandom.current()));
                Component got = loot.give(uuid, data, paid, "server goal " + g.id());
                core.logger().info("[objectives] Server goal paid " + uuid + ": " + paid);
                Player online = plugin.getServer().getPlayer(uuid);
                if (online != null) {
                    online.sendMessage(ItemLook.text("✦ For your part in the goal: ", BLUE).append(got));
                }
            });
        }
    }

    /** "Ender Hunt · 1,204 / 5,000 · you 212 (4%)" for the Objectives page. */
    Component line(UUID player) {
        ObjectiveDefinition g = goal();
        if (g == null) {
            return ItemLook.text("No server goal this week.", ObjectivesService.INK);
        }
        long total = ServerGoalRules.total(state.contributions);
        long mine = state.contributions.getOrDefault(player.toString(), 0L);
        return ItemLook.text(g.name(), state.completed ? ObjectivesService.DONE : ObjectivesService.PAPER)
                .append(ItemLook.text(" · " + g.task() + " together", ObjectivesService.INK)).append(Component.newline())
                .append(ItemLook.text((state.completed ? "✔ Done · " : "") + FeatRules.number(Math.min(total, g.target())) + " / "
                        + FeatRules.number(g.target()), BLUE))
                .append(ItemLook.text("   your part " + FeatRules.number(mine)
                        + (mine > 0 ? " (" + ServerGoalRules.share(mine, g.target()) + "%)" : ""), ObjectivesService.INK));
    }
}
