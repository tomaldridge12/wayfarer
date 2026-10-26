package dev.wayfarer.objectives;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.progression.ProgressChangedEvent;
import dev.wayfarer.core.progression.Progression;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import org.bukkit.GameRule;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Weekend events: the rotation's event starts with the weekend window (or by command) and switches on its modifier,
 * which undoes itself when the event ends or the module stops (a changed game rule is saved, so even a crash puts it
 * back on the next start). Each event has its own server goal, paying every contributor an event-tier roll. Main thread.
 */
final class Events implements Listener {
    static final int VIOLET = 0xB58CE6;

    /** The rotation and events as last loaded. */
    record Config(List<String> rotation, Map<String, EventDefinition> events) {
        static Config empty() {
            return new Config(List.of(), Map.of());
        }
    }

    private final JavaPlugin plugin;
    private final Core core;
    private final Supplier<ObjectivesService.State> pools;
    private final Supplier<Config> config;
    private final ServerGoals goals;
    private final ObjectivesService service;
    private final Loot loot;
    private BukkitTask tick;

    Events(JavaPlugin plugin, Core core, Supplier<ObjectivesService.State> pools, Supplier<Config> config, ServerGoals goals,
            ObjectivesService service, Loot loot) {
        this.plugin = plugin;
        this.core = core;
        this.pools = pools;
        this.config = config;
        this.goals = goals;
        this.service = service;
        this.loot = loot;
    }

    private ServerGoalState state() {
        return goals.state();
    }

    /** The running event, or null. */
    EventDefinition running() {
        return state().event == null ? null : config.get().events().get(state().event);
    }

    void start() {
        restore(); // whatever a crash left changed
        EventDefinition e = running();
        if (e != null) {
            apply(e);
        }
        reconcile();
        tick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::reconcile, 600, 600);
        core.logger().info("Events loaded: " + config.get().events().size() + " events, rotation " + config.get().rotation()
                + (running() == null ? "" : "; running " + running().id()));
    }

    /** Puts the world back while the module stops; the event itself stays recorded and resumes on the next start. */
    void stop() {
        if (tick != null) {
            tick.cancel();
        }
        restore();
    }

    /** Starts the weekend's event when its window opens and ends a scheduled one when the window closes. */
    void reconcile() {
        ServerGoalState s = state();
        Optional<Instant[]> window = pools.get().settings().schedule().event(Instant.now());
        if (s.event != null && !s.eventForced && (window.isEmpty() || s.eventStart != window.get()[0].toEpochMilli())) {
            end("its weekend is over");
        }
        if (s.event == null && window.isPresent() && s.eventStart != window.get()[0].toEpochMilli()) {
            String id = EventRules.forWeekend(config.get().rotation(), window.get()[0], pools.get().settings().schedule().zone());
            if (id != null) {
                begin(config.get().events().get(id), window.get()[0].toEpochMilli(), false);
            }
        }
    }

    void begin(EventDefinition e, long startMillis, boolean forced) {
        if (running() != null) {
            end("another event begins");
        }
        ServerGoalState s = state();
        s.event = e.id();
        s.eventStart = startMillis;
        s.eventForced = forced;
        s.eventGoal = e.goal().id();
        s.eventBaselines.clear();
        s.eventContributions.clear();
        s.eventPaid.clear();
        s.eventCompleted = false;
        goals.markDirty();
        plugin.getServer().getOnlinePlayers().forEach(this::seen);
        String change = apply(e);
        core.logger().info("Event " + e.id() + " started" + (forced ? " by command" : "") + ": " + change);
        plugin.getServer().broadcast(ItemLook.text("✦ " + e.name() + " has begun! ", VIOLET).append(ItemLook.text(e.blurb(), ObjectivesService.PAPER)));
        plugin.getServer().broadcast(ItemLook.text("   Together: " + e.goal().task(), ObjectivesService.INK));
        plugin.getServer().getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.4f));
    }

    void end(String why) {
        EventDefinition e = running();
        ServerGoalState s = state();
        if (e == null) {
            s.event = null;
            return;
        }
        String change = restore();
        s.event = null;
        s.eventForced = false;
        // Stopped during a weekend window, it stays stopped for the rest of that weekend.
        pools.get().settings().schedule().event(Instant.now()).ifPresent(w -> s.eventStart = w[0].toEpochMilli());
        goals.markDirty();
        core.logger().info("Event " + e.id() + " ended (" + why + "): " + change);
        plugin.getServer().broadcast(ItemLook.text("✦ " + e.name() + " is over. ", VIOLET).append(ItemLook.text(
                s.eventCompleted ? "Its goal was met: well played." : "Its goal ended at " + FeatRules.number(ServerGoalRules.total(s.eventContributions))
                        + " / " + FeatRules.number(e.goal().target()) + ".", ObjectivesService.INK)));
    }

    /** Switches the modifier on; says what changed, for the log. */
    private String apply(EventDefinition e) {
        if (e.modifier().equals("crop_growth")) {
            int speed = (int) e.number("tick-speed", 12);
            List<String> changed = new ArrayList<>();
            for (World w : plugin.getServer().getWorlds()) {
                if (w.getEnvironment() != World.Environment.NORMAL) {
                    continue;
                }
                Integer old = w.getGameRuleValue(GameRule.RANDOM_TICK_SPEED);
                state().eventRestore.putIfAbsent(w.getName(), old == null ? 3L : old.longValue());
                w.setGameRule(GameRule.RANDOM_TICK_SPEED, speed);
                changed.add(w.getName() + " random tick speed " + old + " -> " + speed);
            }
            goals.markDirty();
            goals.save(true);
            return String.join(", ", changed);
        }
        return e.modifier() + " on";
    }

    /** Puts back what a modifier changed; says what, for the log. */
    private String restore() {
        ServerGoalState s = state();
        if (s.eventRestore.isEmpty()) {
            return "nothing to put back";
        }
        List<String> back = new ArrayList<>();
        s.eventRestore.forEach((name, speed) -> {
            World w = plugin.getServer().getWorld(name);
            if (w != null) {
                w.setGameRule(GameRule.RANDOM_TICK_SPEED, speed.intValue());
                back.add(name + " random tick speed back to " + speed);
            }
        });
        s.eventRestore.clear();
        goals.markDirty();
        goals.save(true);
        return String.join(", ", back);
    }

    // --- the modifiers ---

    private boolean active(String modifier) {
        EventDefinition e = running();
        return e != null && e.modifier().equals(modifier);
    }

    /** The Harvest Feast: how many times as often forage turns up right now (1 when no feast is on). The foods module asks. */
    double forageMultiplier() {
        EventDefinition e = running();
        return e != null && e.modifier().equals("forage_chance") ? e.number("multiplier", 2) : 1.0;
    }

    /** The Drowned Tide: every fish caught is caught twice. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    void onFish(PlayerFishEvent event) {
        if (active("double_catch") && event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item item) {
            ItemStack copy = item.getItemStack().clone();
            event.getPlayer().getInventory().addItem(copy).values()
                    .forEach(left -> event.getPlayer().getWorld().dropItem(event.getPlayer().getLocation(), left));
        }
    }

    /** Deep Rush: ores give more experience. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onBreak(BlockBreakEvent event) {
        EventDefinition e = running();
        if (e != null && e.modifier().equals("ore_xp") && event.getExpToDrop() > 0) {
            event.setExpToDrop(EventRules.multiply(event.getExpToDrop(), e.number("multiplier", 2)));
        }
    }

    /** Bone Night: the listed mobs drop twice what they would. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDeath(EntityDeathEvent event) {
        EventDefinition e = running();
        if (e == null || !e.modifier().equals("mob_drops") || event.getEntity().getKiller() == null
                || !(e.settings().get("mobs") instanceof List<?> mobs) || !mobs.contains(event.getEntityType().getKey().getKey())) {
            return;
        }
        int times = (int) e.number("multiplier", 2);
        List<ItemStack> extra = new ArrayList<>();
        for (int i = 1; i < times; i++) {
            event.getDrops().forEach(d -> extra.add(d.clone()));
        }
        event.getDrops().addAll(extra);
    }

    // --- the event's own goal ---

    private void seen(Player player) {
        EventDefinition e = running();
        String id = player.getUniqueId().toString();
        Optional<Progression.Snapshot> snap = core.progression().snapshot(player.getUniqueId());
        if (e != null && snap.isPresent() && !state().eventBaselines.containsKey(id)) {
            state().eventBaselines.put(id, ObjectiveRules.value(e.goal(), snap.get().counters()));
            goals.markDirty();
        }
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
        EventDefinition e = running();
        ServerGoalState s = state();
        if (e == null || s.eventCompleted) {
            return;
        }
        String id = event.player().toString();
        Long baseline = s.eventBaselines.get(id);
        Optional<Progression.Snapshot> snap = core.progression().snapshot(event.player());
        if (baseline == null || snap.isEmpty()) {
            return;
        }
        long now = Math.max(0, ObjectiveRules.value(e.goal(), snap.get().counters()) - baseline);
        if (now != s.eventContributions.getOrDefault(id, 0L)) {
            contribute(id, now);
        }
    }

    void contribute(String id, long contribution) {
        EventDefinition e = running();
        ServerGoalState s = state();
        if (e == null || s.eventCompleted) {
            return;
        }
        s.eventContributions.put(id, contribution);
        goals.markDirty();
        if (ServerGoalRules.total(s.eventContributions) >= e.goal().target()) {
            s.eventCompleted = true;
            plugin.getServer().broadcast(ItemLook.text("✦ " + e.name() + ": the goal is met! ", VIOLET)
                    .append(ItemLook.text("Everyone who helped gets something from the event's chest.", ObjectivesService.PAPER)));
            core.logger().info("[objectives] Event goal " + e.goal().id() + " completed by " + s.eventContributions.size() + " players");
            for (Map.Entry<String, Long> c : new ArrayList<>(s.eventContributions.entrySet())) {
                if (c.getValue() <= 0 || !s.eventPaid.add(c.getKey())) {
                    continue;
                }
                UUID uuid = UUID.fromString(c.getKey());
                service.with(uuid, (data, snap) -> {
                    List<String> paid = pools.get().tables().roll(e.table(), "event", r -> service.owns(uuid, data, r), ThreadLocalRandom.current());
                    Component got = loot.give(uuid, data, paid, "event " + e.id());
                    Player online = plugin.getServer().getPlayer(uuid);
                    if (online != null) {
                        online.sendMessage(ItemLook.text("✦ For " + e.name() + ": ", VIOLET).append(got));
                    }
                });
            }
        }
    }
}
