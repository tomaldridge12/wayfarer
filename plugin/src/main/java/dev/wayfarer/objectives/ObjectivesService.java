package dev.wayfarer.objectives;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.PlayerStore;
import dev.wayfarer.core.progression.ProgressChangedEvent;
import dev.wayfarer.core.progression.Progression;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Dailies and the weekly on the server: draws at each window's start by the player's stage, progress from their
 * counters, completion paying tokens and a reward table roll, rerolls, streaks, and loot kept for offline players.
 * Main thread.
 */
final class ObjectivesService implements Listener {
    static final int GOLD = 0xE8B04A;
    static final int PAPER = 0xE8D9A8;
    static final int INK = 0xB8AC94;
    static final int DONE = 0x8FCB6B;

    /** What the service works from, as last loaded. */
    record State(PoolConfigParser.Settings settings, Map<String, ObjectiveDefinition> daily, Map<String, ObjectiveDefinition> weekly,
            RewardTables tables) {
    }

    private final JavaPlugin plugin;
    private final Core core;
    private final Supplier<State> state;
    private final PlayerStore<ObjectivesPlayerData> store;
    private final Loot loot;
    private BukkitTask tick;
    private BukkitTask save;

    ObjectivesService(JavaPlugin plugin, Core core, Supplier<State> state, Loot loot) {
        this.plugin = plugin;
        this.core = core;
        this.state = state;
        this.loot = loot;
        this.store = new PlayerStore<>(plugin.getDataFolder().toPath().resolve("data").resolve("objectives"),
                ObjectivesPlayerData.VERSION, new ObjectivesPlayerData.Codec(), core.logger(),
                uuid -> plugin.getServer().getPlayer(uuid) != null);
    }

    PlayerStore<ObjectivesPlayerData> store() {
        return store;
    }

    void start() {
        plugin.getServer().getOnlinePlayers().forEach(p -> store.load(p.getUniqueId()).thenRunAsync(() -> joined(p), this::main));
        tick = plugin.getServer().getScheduler().runTaskTimer(plugin, this::everyMinute, 1200, 1200);
        save = plugin.getServer().getScheduler().runTaskTimer(plugin, store::flush, 1200, 1200);
    }

    void stop() {
        if (tick != null) {
            tick.cancel();
        }
        if (save != null) {
            save.cancel();
        }
        store.close();
    }

    private void main(Runnable r) {
        plugin.getServer().getScheduler().runTask(plugin, r);
    }

    /** Runs the action with the player's objectives data and progression record, loading either off the main thread. */
    void with(UUID id, java.util.function.BiConsumer<ObjectivesPlayerData, Progression.Snapshot> action) {
        CompletableFuture.allOf(store.load(id), core.progression().load(id)).whenCompleteAsync((v, error) -> {
            ObjectivesPlayerData data = store.cached(id);
            Optional<Progression.Snapshot> snap = core.progression().snapshot(id);
            if (error != null || data == null || snap.isEmpty()) {
                core.logger().warning("[objectives] Could not load data for " + id + (error == null ? "" : ": " + error.getMessage()));
                return;
            }
            action.accept(data, snap.get());
        }, this::main);
    }

    // --- windows and draws ---

    /** The player's stage: the last stage whose marker advancement they have done (the first needs none). */
    String stage(Player player) {
        PoolConfigParser.Settings s = state.get().settings();
        String reached = s.stages().getFirst();
        for (String stage : s.stages()) {
            String adv = s.stageAdvancements().getOrDefault(stage, "");
            if (adv.isEmpty()) {
                reached = stage;
                continue;
            }
            NamespacedKey key = NamespacedKey.fromString(adv.contains(":") ? adv : "minecraft:" + adv);
            Advancement a = key == null ? null : plugin.getServer().getAdvancement(key);
            if (a != null && player.getAdvancementProgress(a).isDone()) {
                reached = stage;
            }
        }
        return reached;
    }

    /** Draws new dailies or a new weekly when their window has turned over; true if anything changed. */
    boolean refresh(UUID id, ObjectivesPlayerData data, Progression.Snapshot snap, String stage) {
        State s = state.get();
        Instant now = Instant.now();
        boolean changed = false;
        long dailyStart = s.settings().schedule().dailyStart(now).toEpochMilli();
        if (data.dailyStart != dailyStart || data.dailies.isEmpty() && !s.daily().isEmpty()) {
            data.dailyStart = dailyStart;
            data.dailies.clear();
            for (ObjectiveDefinition d : ObjectiveRules.draw(s.daily().values(), s.settings().stages(), stage,
                    s.settings().dailies(), Set.of(), ThreadLocalRandom.current())) {
                data.dailies.add(new ObjectivesPlayerData.Slot(d.id(), ObjectiveRules.value(d, snap.counters()), false));
            }
            changed = true;
        }
        long weeklyStart = s.settings().schedule().weeklyStart(now).toEpochMilli();
        if (data.weeklyStart != weeklyStart || data.weekly == null && !s.weekly().isEmpty()) {
            data.weeklyStart = weeklyStart;
            data.weekly = ObjectiveRules.draw(s.weekly().values(), s.settings().stages(), stage, 1, Set.of(), ThreadLocalRandom.current())
                    .stream().findFirst().map(d -> new ObjectivesPlayerData.Slot(d.id(), ObjectiveRules.value(d, snap.counters()), false))
                    .orElse(null);
            changed = true;
        }
        long today = s.settings().schedule().day(now);
        if (data.rerollDay != today) {
            data.rerollDay = today;
            data.rerollsUsed = 0;
        }
        if (changed) {
            store.markDirty(id);
            core.logger().info("[objectives] Drew for " + id + " at stage " + stage + ": dailies "
                    + data.dailies.stream().map(x -> x.id).toList() + ", weekly " + (data.weekly == null ? "none" : data.weekly.id));
        }
        return changed;
    }

    private void joined(Player player) {
        if (!player.isOnline()) {
            return;
        }
        with(player.getUniqueId(), (data, snap) -> {
            if (refresh(player.getUniqueId(), data, snap, stage(player))) {
                player.sendMessage(ItemLook.text("✦ New objectives are in your Journal. ", GOLD)
                        .append(ItemLook.text("/wayfarer journal", INK)));
            }
            check(player.getUniqueId(), data, snap);
            deliverPending(player, data);
        });
    }

    private void everyMinute() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            ObjectivesPlayerData data = store.cached(p.getUniqueId());
            Optional<Progression.Snapshot> snap = core.progression().snapshot(p.getUniqueId());
            if (data != null && snap.isPresent() && refresh(p.getUniqueId(), data, snap.get(), stage(p))) {
                p.sendMessage(ItemLook.text("✦ A new day: fresh objectives are in your Journal.", GOLD));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.2f);
            }
        }
    }

    @EventHandler
    void onPreLogin(AsyncPlayerPreLoginEvent event) {
        try {
            store.load(event.getUniqueId()).join();
        } catch (RuntimeException e) {
            core.logger().severe("[objectives] Could not load data for " + event.getUniqueId() + ": " + e.getMessage());
        }
    }

    @EventHandler
    void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> joined(player), 40);
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        store.save(event.getPlayer().getUniqueId());
    }

    @EventHandler
    void onProgress(ProgressChangedEvent event) {
        ObjectivesPlayerData data = store.cached(event.player());
        core.progression().snapshot(event.player()).ifPresent(snap -> {
            if (data != null) {
                check(event.player(), data, snap);
            }
        });
    }

    // --- progress and completion ---

    /** Completes every drawn objective whose progress has reached its target. */
    void check(UUID id, ObjectivesPlayerData data, Progression.Snapshot snap) {
        State s = state.get();
        for (int i = 0; i < data.dailies.size(); i++) {
            ObjectivesPlayerData.Slot slot = data.dailies.get(i);
            ObjectiveDefinition d = s.daily().get(slot.id);
            if (!slot.done && d != null && ObjectiveRules.progress(d, snap.counters(), slot.baseline) >= d.target()) {
                complete(id, data, slot, d, "daily");
            }
        }
        if (data.weekly != null && !data.weekly.done) {
            ObjectiveDefinition d = s.weekly().get(data.weekly.id);
            if (d != null && ObjectiveRules.progress(d, snap.counters(), data.weekly.baseline) >= d.target()) {
                complete(id, data, data.weekly, d, "weekly");
            }
        }
    }

    /** Pays an objective: its tokens and one roll of its table's tier; then the streak if every daily is done. */
    void complete(UUID id, ObjectivesPlayerData data, ObjectivesPlayerData.Slot slot, ObjectiveDefinition d, String tier) {
        slot.done = true;
        store.markDirty(id);
        List<String> paid = new ArrayList<>();
        if (d.tokens() > 0) {
            paid.add("tokens:" + d.tokens());
        }
        paid.addAll(state.get().tables().roll(d.table(), tier, reward -> owns(id, data, reward), ThreadLocalRandom.current()));
        core.logger().info("[objectives] " + id + " completed " + tier + " " + d.id() + ", paid " + paid);
        Player online = plugin.getServer().getPlayer(id);
        Component got = loot.give(id, data, paid, tier + " " + d.id());
        if (online != null) {
            online.sendMessage(ItemLook.text("✦ " + (tier.equals("weekly") ? "Weekly" : "Daily") + " complete: ", GOLD)
                    .append(ItemLook.text(d.name(), PAPER)).append(ItemLook.text(" · ", INK)).append(got));
            online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.3f);
        }
        if (tier.equals("daily") && !data.dailies.isEmpty() && data.dailies.stream().allMatch(x -> x.done)) {
            streak(id, data, online);
        }
    }

    private void streak(UUID id, ObjectivesPlayerData data, Player online) {
        PoolConfigParser.Settings s = state.get().settings();
        long today = s.schedule().day(Instant.now());
        ObjectiveRules.Streak streak = ObjectiveRules.completeDay(data.streak, data.lastStreakDay, today, s.streakEvery());
        data.streak = streak.days();
        data.lastStreakDay = today;
        store.markDirty(id);
        if (online != null) {
            online.sendMessage(ItemLook.text("✦ Every daily done. Streak: " + streak.days() + " day" + (streak.days() == 1 ? "" : "s"), DONE));
        }
        if (streak.bonus()) {
            List<String> bonus = new ArrayList<>();
            bonus.add("tokens:" + s.streakTokens());
            if (s.streakReward() != null) {
                bonus.add(s.streakReward());
            }
            Component got = loot.give(id, data, bonus, "streak " + streak.days());
            if (online != null) {
                online.sendMessage(ItemLook.text("✦ A " + streak.days() + "-day streak! ", GOLD).append(got));
                online.playSound(online.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
            }
            core.logger().info("[objectives] " + id + " reached a " + streak.days() + "-day streak, paid " + bonus);
        }
    }

    /** Whether the player already owns a one-of-a-kind reward: a curio they have held, a cosmetic they have. */
    boolean owns(UUID id, ObjectivesPlayerData data, String reward) {
        if (reward.startsWith("cosmetic:")) {
            String c = reward.substring(9);
            return data.cosmetics.contains(c) || core.progression().snapshot(id).map(s -> s.earned().containsKey(reward)).orElse(false);
        }
        if (reward.startsWith("curio:")) {
            String found = "find:wayfarer:curio/" + reward.substring(6);
            return core.progression().snapshot(id).map(s -> s.count(found) > 0).orElse(false);
        }
        return false;
    }

    private void deliverPending(Player player, ObjectivesPlayerData data) {
        if (data.pending.isEmpty()) {
            return;
        }
        List<String> waiting = List.copyOf(data.pending);
        data.pending.clear();
        store.markDirty(player.getUniqueId());
        Component got = loot.give(player.getUniqueId(), data, waiting, "while you were away");
        player.sendMessage(ItemLook.text("✦ Kept for you while you were away: ", GOLD).append(got));
    }

    // --- rerolls ---

    /** What rerolling a daily costs the player now: 0 for the free one, else XP levels; -1 if it cannot be rerolled. */
    int rerollCost(ObjectivesPlayerData data, int slot) {
        PoolConfigParser.Settings s = state.get().settings();
        if (slot < 0 || slot >= data.dailies.size() || data.dailies.get(slot).done) {
            return -1;
        }
        int paid = Math.max(0, data.rerollsUsed - s.freeRerolls());
        return data.rerollsUsed < s.freeRerolls() + extraFree(data) ? 0 : ObjectiveRules.rerollCost(s.rerollBase(), s.rerollStep(), paid);
    }

    /** One more free reroll today when bought in the shop. */
    private int extraFree(ObjectivesPlayerData data) {
        return data.shopRerollDay == state.get().settings().schedule().day(Instant.now()) ? 1 : 0;
    }

    /** Replaces a daily with another the player could draw; false (with a message) when it cannot. */
    boolean reroll(Player player, ObjectivesPlayerData data, Progression.Snapshot snap, int slot, boolean free) {
        int cost = free ? 0 : rerollCost(data, slot);
        if (cost < 0) {
            player.sendMessage(ItemLook.text("That objective can't be rerolled.", INK));
            return false;
        }
        if (cost > 0 && player.getLevel() < cost) {
            player.sendMessage(ItemLook.text("A reroll costs " + cost + " levels; you have " + player.getLevel() + ".", INK));
            return false;
        }
        State s = state.get();
        Set<String> exclude = new HashSet<>();
        data.dailies.forEach(x -> exclude.add(x.id));
        List<ObjectiveDefinition> drawn = ObjectiveRules.draw(s.daily().values(), s.settings().stages(), stage(player), 1, exclude,
                ThreadLocalRandom.current());
        if (drawn.isEmpty()) {
            player.sendMessage(ItemLook.text("There's nothing else to draw at your stage yet.", INK));
            return false;
        }
        if (cost > 0) {
            player.setLevel(player.getLevel() - cost);
        }
        if (!free) {
            data.rerollsUsed++;
        }
        ObjectiveDefinition d = drawn.getFirst();
        data.dailies.set(slot, new ObjectivesPlayerData.Slot(d.id(), ObjectiveRules.value(d, snap.counters()), false));
        store.markDirty(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.1f);
        core.logger().info("[objectives] " + player.getName() + " rerolled daily " + (slot + 1) + " to " + d.id()
                + (cost > 0 ? " for " + cost + " levels" : ""));
        return true;
    }

    /** Adds (or takes, if negative) tokens with a ledger line; false if it would go below zero. */
    boolean tokens(UUID id, ObjectivesPlayerData data, long delta, String why) {
        Tokens.Change c = Tokens.add(data.tokens, delta);
        if (c.applied() != delta) {
            return false;
        }
        data.tokens = c.balance();
        data.log(Tokens.ledger(Instant.now(), c.applied(), why, c.balance()));
        store.markDirty(id);
        core.logger().info("[objectives] Tokens " + id + " " + (delta >= 0 ? "+" : "") + delta + " " + why + " -> " + c.balance());
        return true;
    }

    /** "Tome of Experience, 20 tokens" from loot keys, for messages. */
    static Component list(List<Component> parts) {
        TextComponent.Builder out = Component.text();
        for (int i = 0; i < parts.size(); i++) {
            out.append(i > 0 ? ItemLook.text(", ", INK) : Component.empty()).append(parts.get(i));
        }
        return out.build();
    }
}
