package dev.wayfarer.core.progression;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.PlayerNames;
import dev.wayfarer.core.PlayerStore;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Core's progression on the server: loads progression.yml, keeps each player's progression record, feeds
 * activities from the listener to the engine, and owns the stats and setcount commands.
 */
public final class ProgressionService {
    public static final String CONFIG_ID = "progression";
    static final String OWNER = "core";
    static final String STATS_PERMISSION = "wayfarer.progression.stats";
    static final String SETCOUNT_PERMISSION = "wayfarer.progression.setcount";
    private static final long SAVE_INTERVAL_TICKS = 20L * 60;

    private final JavaPlugin plugin;
    private final Core core;
    private final Executor mainThread;
    private final PlayerStore<ProgressionData> store;
    private volatile ProgressionEngine engine = new ProgressionEngine(ProgressionSettings.defaults());
    private ProgressionListener listener;
    private BukkitTask saveTask;

    public ProgressionService(JavaPlugin plugin, Core core) {
        this.plugin = plugin;
        this.core = core;
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
        this.store = new PlayerStore<>(plugin.getDataFolder().toPath().resolve("data").resolve(CONFIG_ID),
                ProgressionCodec.VERSION, new ProgressionCodec(), core.logger(),
                uuid -> plugin.getServer().getPlayer(uuid) != null);
    }

    public void enable() {
        load();
        plugin.getServer().getOnlinePlayers().forEach(p -> store.load(p.getUniqueId()));
        listener = new ProgressionListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, store::flush,
                SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        core.commands().register(OWNER, statsCommand());
        core.commands().register(OWNER, setCountCommand());
    }

    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        store.close();
    }

    /** Reads progression.yml; an unreadable file keeps the defaults so counting never stops. */
    private void load() {
        ProgressionSettings settings;
        try {
            settings = ProgressionSettings.parse(core.config().load(CONFIG_ID), ProgressionService::isSpawnReason);
        } catch (Exception e) {
            core.logger().log(Level.SEVERE, "progression.yml could not be read; using the defaults", e);
            settings = ProgressionSettings.defaults();
        }
        settings.problems().forEach(p -> core.logger().warning("[progression] " + p));
        engine = new ProgressionEngine(settings);
        CreditRules credit = settings.credit();
        core.logger().info("Progression loaded: " + credit.excludedSpawnReasons().size()
                + " excluded spawn reasons, min lifetime " + credit.minLifetimeTicks() + " ticks, "
                + settings.problems().size() + " problems");
    }

    private static boolean isSpawnReason(String name) {
        try {
            CreatureSpawnEvent.SpawnReason.valueOf(name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // --- events (called by the listener, main thread unless noted) ---

    /** Async pre-login thread, so blocking file I/O is fine here. */
    void loadBlocking(UUID id) {
        try {
            store.load(id).join();
        } catch (RuntimeException e) {
            core.logger().severe("[progression] Could not load data for " + id + ": " + e.getMessage());
        }
    }

    void quit(UUID id) {
        store.save(id);
    }

    void kill(Player killer, KillFacts kill) {
        ProgressionData data = store.cached(killer.getUniqueId());
        if (data == null) {
            return; // data failed to load; already logged
        }
        if (engine.kill(data, kill).counted()) {
            store.markDirty(killer.getUniqueId());
        }
    }

    // --- commands ---

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> statsCommand() {
        return Commands.literal("stats")
                .requires(s -> s.getSender().hasPermission(STATS_PERMISSION))
                .then(player().executes(ctx -> run(() -> stats(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "player"), "")))
                        .then(Commands.argument("prefix", StringArgumentType.greedyString())
                                .executes(ctx -> run(() -> stats(ctx.getSource().getSender(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "prefix").trim())))));
    }

    /** {@code setcount <player> <activity> <n>}; the activity and count are read as one line, as ':' ends a word. */
    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> setCountCommand() {
        return Commands.literal("setcount")
                .requires(s -> s.getSender().hasPermission(SETCOUNT_PERMISSION))
                .then(player().then(Commands.argument("activity count", StringArgumentType.greedyString())
                        .executes(ctx -> run(() -> setCount(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "activity count"))))));
    }

    private void stats(CommandSender sender, String targetName, String prefix) {
        withData(sender, targetName, (id, data) -> {
            Map<String, Long> counters = new java.util.TreeMap<>(data.countersView());
            counters.keySet().removeIf(k -> !k.startsWith(prefix));
            sender.sendMessage("Counters for " + PlayerNames.nameOf(plugin.getServer(), id) + " (" + id + ")"
                    + (prefix.isEmpty() ? "" : " starting '" + prefix + "'") + ": " + counters.size());
            counters.forEach((k, v) -> sender.sendMessage("  " + k + " = " + v));
        });
    }

    private void setCount(CommandSender sender, String targetName, String line) {
        String[] parts = line.trim().split("\\s+");
        long count;
        try {
            count = parts.length == 2 ? Long.parseLong(parts[1]) : -1;
        } catch (NumberFormatException e) {
            count = -1;
        }
        String activity = parts[0];
        if (count < 0 || activity.indexOf(':') <= 0) {
            sender.sendMessage("Usage: /wayfarer setcount <player> <verb:target> <count of 0 or more>, such as kill:zombie 40");
            return;
        }
        long n = count;
        withData(sender, targetName, (id, data) -> {
            engine.set(data, activity, n);
            store.markDirty(id);
            sender.sendMessage("Set " + id + " " + activity + " to " + n);
        });
    }

    /** Resolves the player, loads their record off the main thread, then runs the action back on it. */
    private void withData(CommandSender sender, String targetName, java.util.function.BiConsumer<UUID, ProgressionData> action) {
        UUID id = PlayerNames.resolve(plugin.getServer(), targetName);
        if (id == null) {
            sender.sendMessage("Unknown player '" + targetName + "' (use their UUID if they have not joined recently)");
            return;
        }
        store.load(id).whenCompleteAsync((data, error) -> {
            if (error != null) {
                sender.sendMessage("Could not load data for " + id + ": " + error.getMessage());
            } else {
                action.accept(id, data);
            }
        }, mainThread);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> player() {
        return Commands.argument("player", StringArgumentType.word()).suggests((ctx, builder) -> {
            plugin.getServer().getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
            return builder.buildFuture();
        });
    }

    private static int run(Runnable action) {
        action.run();
        return Command.SINGLE_SUCCESS;
    }
}
