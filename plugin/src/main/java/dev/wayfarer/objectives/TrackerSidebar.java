package dev.wayfarer.objectives;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.Trackable;
import dev.wayfarer.core.progression.ProgressChangedEvent;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Each player's tracked things on the right-hand sidebar: one line each with live progress. Redraws are driven by
 * the counter changes core already raises and merged so a busy tick redraws a player once. Main thread.
 */
final class TrackerSidebar implements Listener {
    private static final String OBJECTIVE = "wf_tracker";
    private static final int SLOTS = 15;
    private static final long REFRESH_TICKS = 20L * 30;

    private final JavaPlugin plugin;
    private final Core core;
    private final ObjectivesService service;
    private final IntSupplier cap;
    private final Set<UUID> scheduled = new HashSet<>();
    private final Set<UUID> shown = new HashSet<>();
    private BukkitTask refresh;

    TrackerSidebar(JavaPlugin plugin, Core core, ObjectivesService service, IntSupplier cap) {
        this.plugin = plugin;
        this.core = core;
        this.service = service;
        this.cap = cap;
    }

    void start() {
        // Catches what no event announces: a daily that turned over, or a tracked thing that stopped existing.
        refresh = plugin.getServer().getScheduler().runTaskTimer(plugin, () ->
                plugin.getServer().getOnlinePlayers().forEach(p -> redrawSoon(p.getUniqueId())), REFRESH_TICKS, REFRESH_TICKS);
        plugin.getServer().getOnlinePlayers().forEach(p -> redrawSoon(p.getUniqueId()));
    }

    void stop() {
        if (refresh != null) {
            refresh.cancel();
        }
        for (UUID id : List.copyOf(shown)) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                clear(p);
            }
        }
        shown.clear();
        scheduled.clear();
    }

    /** Asks for a redraw this tick; any number of asks before it runs become one. */
    void redrawSoon(UUID id) {
        if (scheduled.add(id)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                scheduled.remove(id);
                redraw(id);
            });
        }
    }

    /** Tracks the thing for the player (refused at the cap) and says what happened. */
    void track(Player player, ObjectivesPlayerData data, String trackedId, String name) {
        switch (TrackerRules.add(data.tracked, trackedId, cap.getAsInt())) {
            case ADDED -> {
                service.store().markDirty(player.getUniqueId());
                player.sendMessage(ItemLook.text("✦ Tracking ", ObjectivesService.GOLD).append(ItemLook.text(name, ObjectivesService.PAPER))
                        .append(ItemLook.text(" on your sidebar", ObjectivesService.INK)));
                if (data.sidebarHidden) {
                    player.sendMessage(ItemLook.text("The sidebar is hidden: /wayfarer track on shows it.", ObjectivesService.INK));
                }
            }
            case ALREADY -> player.sendMessage(ItemLook.text("You are already tracking " + name + ".", ObjectivesService.INK));
            case FULL -> player.sendMessage(ItemLook.text("You can track " + cap.getAsInt() + " things at once. Untrack one first.",
                    ObjectivesService.INK));
        }
        redrawSoon(player.getUniqueId());
    }

    void untrack(Player player, ObjectivesPlayerData data, String trackedId, String name) {
        if (TrackerRules.remove(data.tracked, trackedId)) {
            service.store().markDirty(player.getUniqueId());
            player.sendMessage(ItemLook.text("✦ Stopped tracking ", ObjectivesService.GOLD).append(ItemLook.text(name, ObjectivesService.PAPER)));
        }
        redrawSoon(player.getUniqueId());
    }

    void setHidden(Player player, ObjectivesPlayerData data, boolean hidden) {
        data.sidebarHidden = hidden;
        service.store().markDirty(player.getUniqueId());
        player.sendMessage(ItemLook.text(hidden ? "Sidebar hidden. /wayfarer track on brings it back." : "Sidebar shown.", ObjectivesService.INK));
        redrawSoon(player.getUniqueId());
    }

    private void redraw(UUID id) {
        Player player = plugin.getServer().getPlayer(id);
        ObjectivesPlayerData data = store(id);
        if (player == null || data == null) {
            return;
        }
        int before = data.tracked.size();
        List<Trackable> live = TrackerRules.live(data.tracked, tracked -> core.trackables().view(id, tracked));
        if (data.tracked.size() != before) {
            service.store().markDirty(id);
        }
        if (data.sidebarHidden || live.isEmpty()) {
            clear(player);
            return;
        }
        Scoreboard board = core.boards().of(player);
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, ItemLook.text("Tracking", ObjectivesService.GOLD));
            objective.numberFormat(NumberFormat.blank());
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        int lines = Math.min(live.size(), SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            String entry = "track_" + i;
            if (i < lines) {
                Trackable t = live.get(i);
                var score = objective.getScore(entry);
                score.setScore(lines - i);
                score.customName(ItemLook.text(t.title() + " ", ObjectivesService.PAPER)
                        .append(ItemLook.text(FeatRules.number(t.progress()) + "/" + FeatRules.number(t.target()), ObjectivesService.GOLD)));
            } else {
                board.resetScores(entry);
            }
        }
        shown.add(id);
    }

    /** Takes the sidebar off the player's screen. */
    private void clear(Player player) {
        if (!shown.remove(player.getUniqueId())) {
            return;
        }
        Scoreboard board = core.boards().of(player);
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective != null) {
            objective.unregister();
        }
    }

    private ObjectivesPlayerData store(UUID id) {
        return service.store().cached(id);
    }

    @EventHandler
    void onProgress(ProgressChangedEvent event) {
        ObjectivesPlayerData data = store(event.player());
        if (data != null && !data.tracked.isEmpty()) {
            redrawSoon(event.player());
        }
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> redrawSoon(id), 60); // after the draw on join
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        shown.remove(id);
        scheduled.remove(id);
        core.boards().release(id);
    }
}
