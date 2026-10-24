package dev.wayfarer.core.progression;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import dev.wayfarer.core.BossDefeatedEvent;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.PlayerNames;
import dev.wayfarer.core.PlayerStore;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Core's progression on the server: loads progression.yml, keeps each player's progression record, feeds
 * activities from the listener to the engine and acts on the outcome (rewards, the earning moment), holds the
 * registered ladders and reward types, and owns the progress, claim, stats and setcount commands.
 */
public final class ProgressionService implements Progression {
    public static final String CONFIG_ID = "progression";
    static final String OWNER = "core";
    // The banners module's nodes, kept so existing permissions still work now that core owns these commands.
    static final String PROGRESS_PERMISSION = "wayfarer.banners.progress";
    static final String PROGRESS_OTHERS_PERMISSION = "wayfarer.banners.progress.others";
    static final String CLAIM_PERMISSION = "wayfarer.banners.claim";
    static final String STATS_PERMISSION = "wayfarer.progression.stats";
    static final String SETCOUNT_PERMISSION = "wayfarer.progression.setcount";
    private static final long SAVE_INTERVAL_TICKS = 20L * 60;
    private static final long PRESENCE_TICKS = 40;
    private static final NamespacedKey VISITORS = new NamespacedKey("wayfarer", "progression_visitors");

    private record OwnedType(String owner, RewardType type) {
    }

    private final JavaPlugin plugin;
    private final Core core;
    private final Executor mainThread;
    private final PlayerStore<ProgressionData> store;
    private final Ladders ladders = new Ladders();
    private final Map<String, OwnedType> rewardTypes = new LinkedHashMap<>();
    private final EarnMoment moment;
    private final SnapshotWriter snapshots;
    private volatile ProgressionEngine engine;
    private ProgressionListener listener;
    private BukkitTask saveTask;
    private BukkitTask presenceTask;

    public ProgressionService(JavaPlugin plugin, Core core) {
        this.plugin = plugin;
        this.core = core;
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
        this.moment = new EarnMoment(plugin.getServer());
        this.snapshots = new SnapshotWriter(plugin.getDataFolder().toPath().resolve("stage-snapshots.csv"), core.logger());
        this.engine = new ProgressionEngine(ProgressionSettings.defaults(), ladders, System::currentTimeMillis);
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
        presenceTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::presence,
                PRESENCE_TICKS, PRESENCE_TICKS);
        core.commands().register(OWNER, progressCommand());
        core.commands().register(OWNER, claimCommand());
        core.commands().register(OWNER, statsCommand());
        core.commands().register(OWNER, setCountCommand());
    }

    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (presenceTask != null) {
            presenceTask.cancel();
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        store.close();
        snapshots.close();
    }

    /** Reads progression.yml; an unreadable file keeps the defaults so counting never stops. */
    private void load() {
        ProgressionSettings settings;
        try {
            settings = ProgressionSettings.parse(core.config().load(CONFIG_ID), new PaperNames());
        } catch (Exception e) {
            core.logger().log(Level.SEVERE, "progression.yml could not be read; using the defaults", e);
            settings = ProgressionSettings.defaults();
        }
        settings.problems().forEach(p -> core.logger().warning("[progression] " + p));
        engine = new ProgressionEngine(settings, ladders, System::currentTimeMillis);
        CreditRules credit = settings.credit();
        core.logger().info("Progression loaded: " + credit.excludedSpawnReasons().size()
                + " excluded spawn reasons, min lifetime " + credit.minLifetimeTicks() + " ticks, "
                + settings.problems().size() + " problems");
        core.logger().info("Pity table loaded: " + settings.pity().size() + " drops ("
                + String.join(", ", settings.pity().stream().map(p -> p.id() + " every " + p.threshold()).toList()) + ")");
        core.logger().info("Stage markers loaded: " + settings.snapshots().markers().size() + " ("
                + String.join(", ", settings.snapshots().markers()) + ")");
    }

    /** The server's own spawn reasons, items and mob types. */
    private static final class PaperNames implements ProgressionSettings.Names {
        @Override
        public boolean isSpawnReason(String name) {
            try {
                CreatureSpawnEvent.SpawnReason.valueOf(name);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        @Override
        public boolean isItem(String name) {
            NamespacedKey key = NamespacedKey.fromString(name);
            return key != null && org.bukkit.Registry.ITEM.get(key) != null;
        }

        @Override
        public boolean isMob(String name) {
            NamespacedKey key = NamespacedKey.fromString(name);
            org.bukkit.entity.EntityType type = key == null ? null : org.bukkit.Registry.ENTITY_TYPE.get(key);
            return type != null && type.isAlive();
        }

        @Override
        public boolean isAdvancement(String name) {
            NamespacedKey key = NamespacedKey.fromString(name);
            return key != null && org.bukkit.Bukkit.getAdvancement(key) != null;
        }
    }

    /** A marker advancement snapshots every counter to stage-snapshots.csv, written off the main thread. */
    void advancement(Player player, NamespacedKey key) {
        ProgressionData data = store.cached(player.getUniqueId());
        if (data == null) {
            return;
        }
        engine.settings().snapshots().line(Activity.target(key.getNamespace(), key.getKey()), System.currentTimeMillis(),
                player.getUniqueId(), player.getName(), data.countersView()).ifPresent(line -> {
                    snapshots.append(line);
                    core.logger().info("[progression] Stage snapshot for " + player.getName() + ": " + key.getKey());
                });
    }

    // --- the module API ---

    @Override
    public void registerRewardType(String owner, String type, RewardType rewardType) {
        OwnedType existing = rewardTypes.get(type);
        if (existing != null && !existing.owner().equals(owner)) {
            throw new IllegalArgumentException("Reward type " + type + " is already registered by " + existing.owner());
        }
        rewardTypes.put(type, new OwnedType(owner, rewardType));
    }

    @Override
    public void setLadders(String owner, List<Ladder> list) {
        ladders.set(owner, list);
        core.logger().info("Ladders registered by " + owner + ": " + list.size() + " ("
                + String.join(", ", list.stream().map(Ladder::id).toList()) + ")");
    }

    @Override
    public void drop(String owner) {
        ladders.drop(owner);
        rewardTypes.values().removeIf(t -> t.owner().equals(owner));
    }

    @Override
    public void record(UUID player, String activity, String how) {
        withData(player, data -> apply(player, engine.record(data, activity), how));
    }

    @Override
    public void award(UUID player, String reward, String how, boolean announce) {
        withData(player, data -> {
            if (!engine.award(data, reward)) {
                return; // awarded once per player; claim recovers lost copies
            }
            store.markDirty(player);
            logEarned(player, reward, how);
            deliverEarned(player, reward, announce);
        });
    }

    @Override
    public OptionalLong progressIfLoaded(UUID player, String ladderId) {
        ProgressionData data = store.cached(player);
        Optional<Ladder> ladder = ladders.find(ladderId);
        return data == null || ladder.isEmpty() ? OptionalLong.empty() : OptionalLong.of(ladder.get().progress(data));
    }

    @Override
    public CompletableFuture<Void> load(UUID player) {
        return store.load(player).thenApplyAsync(d -> null, mainThread); // completes on the main thread
    }

    // --- events (called by the listener, main thread unless noted) ---

    /** Async pre-login thread, so blocking file I/O is fine here. */
    /** Runs on the next tick (main thread). */
    void later(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

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

    /** Every player who damaged the boss is credited with its defeat. */
    void defeated(BossDefeatedEvent event) {
        String boss = event.boss().bossId();
        event.damagers().forEach(id -> record(id, Activity.DEFEAT + ":" + boss, "defeating " + boss));
    }

    /** @return the items pity adds to this death's drops (empty when none is due) */
    List<String> kill(Player killer, KillFacts kill, String heldItem, java.util.Set<String> droppedItems) {
        ProgressionEngine.Outcome outcome = act(killer, (e, data) -> e.kill(data, kill, heldItem, droppedItems));
        List<String> pity = outcome == null ? List.of() : outcome.pityItems();
        pity.forEach(item -> core.logger().info("[progression] Pity drop " + item + " for " + killer.getUniqueId()
                + " from " + kill.mobType()));
        return pity;
    }

    /** Runs one engine call on an online player's record and acts on the outcome; null if the record is not loaded. */
    ProgressionEngine.Outcome act(Player player,
            java.util.function.BiFunction<ProgressionEngine, ProgressionData, ProgressionEngine.Outcome> what) {
        UUID id = player.getUniqueId();
        ProgressionData data = store.cached(id);
        if (data == null) {
            return null; // data failed to load; already logged
        }
        ProgressionEngine.Outcome outcome = what.apply(engine, data);
        apply(id, outcome, null);
        return outcome;
    }

    /**
     * The presence check: the biome each online player stands in, and the generated structures whose bounds
     * contain them. A structure remembers who has entered it, so each instance counts once per player.
     */
    private void presence() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location at = player.getLocation();
            NamespacedKey biome = org.bukkit.Registry.BIOME.getKey(at.getBlock().getBiome());
            if (biome != null) {
                act(player, (e, data) -> e.discoverBiome(data, Activity.target(biome.getNamespace(), biome.getKey())));
            }
            for (GeneratedStructure structure : at.getChunk().getStructures()) {
                NamespacedKey key = org.bukkit.Registry.STRUCTURE.getKey(structure.getStructure());
                if (key == null || !structure.getBoundingBox().contains(at.toVector())) {
                    continue;
                }
                String visitors = structure.getPersistentDataContainer().get(VISITORS, PersistentDataType.STRING);
                if (StructureVisitors.firstVisit(visitors, player.getUniqueId()) && store.cached(player.getUniqueId()) != null) {
                    structure.getPersistentDataContainer().set(VISITORS, PersistentDataType.STRING,
                            StructureVisitors.with(visitors, player.getUniqueId()));
                    act(player, (e, data) -> e.discoverStructure(data, Activity.target(key.getNamespace(), key.getKey()), true));
                }
            }
        }
    }

    /** Saves, redraws and rewards after an outcome. */
    private void apply(UUID id, ProgressionEngine.Outcome outcome, String how) {
        if (!outcome.counted()) {
            return;
        }
        store.markDirty(id);
        plugin.getServer().getPluginManager().callEvent(new ProgressChangedEvent(id, outcome.raised()));
        for (ProgressionEngine.Earned earned : outcome.earned()) {
            logEarned(id, earned.reward(), how);
            deliverEarned(id, earned.reward(), earned.ladder().announces(earned.reward()));
        }
    }

    private void logEarned(UUID id, String reward, String how) {
        core.logger().info("[progression] " + id + " earned " + reward.replaceFirst(":", " ")
                + (how == null ? "" : " by " + how));
    }

    /** Delivers a newly earned reward if the earner is online, and makes it a moment (title, pop, announcement). */
    private void deliverEarned(UUID earner, String reward, boolean announce) {
        Player online = plugin.getServer().getPlayer(earner);
        String name = online != null ? online.getName()
                : Optional.ofNullable(plugin.getServer().getOfflinePlayer(earner).getName()).orElse("A Wayfarer");
        ItemStack item = online != null ? deliver(online, reward) : build(earner, name, reward);
        moment.celebrate(online, name, title(reward), item, announce);
    }

    /** Hands a player a copy of a reward and returns it, or null if no type can make it. */
    private ItemStack deliver(Player player, String reward) {
        ItemStack item = build(player.getUniqueId(), player.getName(), reward);
        if (item != null) {
            player.getInventory().addItem(item.clone()).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
        return item;
    }

    private ItemStack build(UUID earner, String earnerName, String reward) {
        RewardType type = typeOf(reward);
        if (type == null) {
            return null;
        }
        ProgressionData data = store.cached(earner);
        return type.create(idOf(reward), earner, earnerName, data == null ? 0 : data.earnedAt(reward));
    }

    private RewardType typeOf(String reward) {
        int colon = reward.indexOf(':');
        OwnedType owned = colon < 0 ? null : rewardTypes.get(reward.substring(0, colon));
        return owned == null ? null : owned.type();
    }

    private static String idOf(String reward) {
        return reward.substring(reward.indexOf(':') + 1);
    }

    private Component title(String reward) {
        RewardType type = typeOf(reward);
        Component title = type == null ? null : type.title(idOf(reward));
        return title != null ? title : Component.text(reward, NamedTextColor.WHITE);
    }

    private Component describe(String reward) {
        RewardType type = typeOf(reward);
        Component name = type == null ? null : type.describe(idOf(reward));
        return name != null ? name : Component.text(reward, NamedTextColor.WHITE);
    }

    /** "banner:zombie" as written, or a bare "zombie" read as the first type that knows that id; null if none. */
    private String parseReward(String text) {
        String trimmed = text.trim();
        int colon = trimmed.indexOf(':');
        if (colon >= 0) {
            String key = trimmed.substring(0, colon).toLowerCase(java.util.Locale.ROOT) + trimmed.substring(colon);
            RewardType type = typeOf(key);
            return type != null && type.title(idOf(key)) != null ? key : null;
        }
        for (Map.Entry<String, OwnedType> entry : rewardTypes.entrySet()) {
            if (entry.getValue().type().title(trimmed) != null) {
                return entry.getKey() + ":" + trimmed;
            }
        }
        return null;
    }

    // --- commands (all run on the main thread) ---

    private LiteralArgumentBuilder<CommandSourceStack> progressCommand() {
        return Commands.literal("progress")
                .requires(s -> s.getSender().hasPermission(PROGRESS_PERMISSION))
                .executes(ctx -> run(() -> progress(ctx.getSource().getSender(), null)))
                .then(player().requires(s -> s.getSender().hasPermission(PROGRESS_OTHERS_PERMISSION))
                        .executes(ctx -> run(() -> progress(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "player")))));
    }

    /** The reward reads the rest of the line, because single-word arguments cannot contain ':'. */
    private LiteralArgumentBuilder<CommandSourceStack> claimCommand() {
        return Commands.literal("claim")
                .requires(s -> s.getSender().hasPermission(CLAIM_PERMISSION))
                .then(Commands.argument("reward", StringArgumentType.greedyString()).suggests((ctx, builder) -> {
                    ProgressionData data = ctx.getSource().getSender() instanceof Player p ? store.cached(p.getUniqueId()) : null;
                    if (data != null) {
                        data.earnedView().keySet().stream().sorted().forEach(builder::suggest);
                    }
                    return builder.buildFuture();
                }).executes(ctx -> run(() -> claim(ctx.getSource().getSender(),
                        StringArgumentType.getString(ctx, "reward")))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> statsCommand() {
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
    private LiteralArgumentBuilder<CommandSourceStack> setCountCommand() {
        return Commands.literal("setcount")
                .requires(s -> s.getSender().hasPermission(SETCOUNT_PERMISSION))
                .then(player().then(Commands.argument("activity count", StringArgumentType.greedyString())
                        .executes(ctx -> run(() -> setCount(ctx.getSource().getSender(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "activity count"))))));
    }

    private void progress(CommandSender sender, String targetName) {
        if (targetName == null && !(sender instanceof Player)) {
            sender.sendMessage("Name a player: /wayfarer progress <player>");
            return;
        }
        String who = targetName != null ? targetName : sender.getName();
        withData(sender, who, (id, data) -> {
            boolean own = sender instanceof Player p && p.getUniqueId().equals(id);
            ProgressionText.progress(PlayerNames.nameOf(plugin.getServer(), id), ladders.all(), data,
                    this::describe, own).forEach(sender::sendMessage);
        });
    }

    private void claim(CommandSender sender, String rewardText) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can claim rewards");
            return;
        }
        ProgressionData data = store.cached(player.getUniqueId());
        if (data == null) {
            sender.sendMessage("Your reward data is not loaded; try again shortly");
            return;
        }
        String reward = parseReward(rewardText);
        if (reward == null) {
            sender.sendMessage("Unknown reward '" + rewardText.trim() + "'");
            return;
        }
        String plainName = PlainTextComponentSerializer.plainText().serialize(title(reward));
        switch (engine.claim(data, reward)) {
            case NOT_EARNED -> sender.sendMessage("You have not earned the " + plainName + " yet");
            case COOLDOWN -> {
                long minutes = (engine.claimWait(data, reward) + 59_999) / 60_000;
                sender.sendMessage("You can claim another copy in " + (minutes >= 60
                        ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m"));
            }
            case OK -> {
                store.markDirty(player.getUniqueId());
                deliver(player, reward);
                sender.sendMessage("Here is your " + plainName);
            }
        }
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
            ProgressionEngine.Outcome outcome = engine.set(data, activity, n);
            apply(id, outcome, null);
            Player online = plugin.getServer().getPlayer(id);
            sender.sendMessage("Set " + id + " " + activity + " to " + n + "; newly earned: "
                    + outcome.earned().stream().map(ProgressionEngine.Earned::reward).toList()
                    + (online == null && !outcome.earned().isEmpty() ? " (offline: use /wayfarer claim)" : ""));
        });
    }

    /** Loads the record off the main thread, then runs the action back on it; logs a failed load. */
    private void withData(UUID id, java.util.function.Consumer<ProgressionData> action) {
        store.load(id).whenCompleteAsync((data, error) -> {
            if (error != null) {
                core.logger().severe("[progression] Could not load data for " + id + ": " + error.getMessage());
            } else {
                action.accept(data);
            }
        }, mainThread);
    }

    /** Resolves the player, loads their record off the main thread, then runs the action back on it. */
    private void withData(CommandSender sender, String targetName, BiConsumer<UUID, ProgressionData> action) {
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
