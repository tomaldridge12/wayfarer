package dev.wayfarer.banners;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.Module;
import dev.wayfarer.core.PlayerStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Trophy banners: config, kill counting, earning, storage and commands. */
public final class BannersModule implements Module {
    static final String RELOAD_PERMISSION = "wayfarer.banners.reload";
    static final String GIVE_PERMISSION = "wayfarer.banners.give";
    static final String SETKILLS_PERMISSION = "wayfarer.banners.setkills";
    static final String PROGRESS_PERMISSION = "wayfarer.banners.progress";
    static final String PROGRESS_OTHERS_PERMISSION = "wayfarer.banners.progress.others";
    static final String CLAIM_PERMISSION = "wayfarer.banners.claim";
    private static final long SAVE_INTERVAL_TICKS = 20L * 60;

    private final JavaPlugin plugin;
    private final Vocabulary vocabulary = new PaperVocabulary();
    private final Executor mainThread;
    private Core core;
    private BannerItems items;
    private PlayerStore<PlayerData> store;
    private BannerListener listener;
    private BukkitTask saveTask;
    private volatile BannerSet loaded = BannerSet.empty();

    public BannersModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public String id() {
        return "banners";
    }

    @Override
    public void enable(Core core) throws Exception {
        this.core = core;
        this.items = new BannerItems(core.itemTags());
        load(); // throws if the file is unreadable, which skips only this module
        store = new PlayerStore<>(plugin.getDataFolder().toPath().resolve("data").resolve(id()),
                PlayerDataCodec.VERSION, new PlayerDataCodec(), core.logger(),
                uuid -> plugin.getServer().getPlayer(uuid) != null);
        plugin.getServer().getOnlinePlayers().forEach(p -> store.load(p.getUniqueId()));
        listener = new BannerListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        saveTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, store::flush, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(BannerCommands.build(this)));
    }

    @Override
    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (store != null) {
            store.close();
        }
        loaded = BannerSet.empty();
    }

    BannerSet banners() {
        return loaded;
    }

    List<String> onlineNames() {
        return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
    }

    // --- events (called by the listener) ---

    void loadPlayerBlocking(UUID id) {
        try {
            store.load(id).join();
        } catch (RuntimeException e) {
            core.logger().severe("[banners] Could not load data for " + id + ": " + e.getMessage());
        }
    }

    void savePlayer(UUID id) {
        store.save(id);
    }

    void onKill(Player killer, Kill kill) {
        PlayerData data = store.cached(killer.getUniqueId());
        if (data == null) {
            return; // data failed to load; already logged
        }
        BannerRules.KillResult result = BannerRules.recordKill(data, kill, loaded);
        if (result.counted()) {
            store.markDirty(killer.getUniqueId());
            result.newlyEarned().forEach(def -> award(killer, def));
        }
    }

    private void award(Player player, BannerDefinition def) {
        core.logger().info("[banners] " + player.getUniqueId() + " earned banner " + def.id());
        items.deliver(player, def);
        player.sendMessage("You earned the " + def.name() + "!");
    }

    // --- commands (all run on the main thread) ---

    void reload(CommandSender sender) {
        try {
            load();
            sender.sendMessage("Banners reloaded: " + loaded.banners().size() + " banners, "
                    + loaded.problems().size() + " problems (see log)");
        } catch (IOException e) {
            core.logger().severe("[banners] Reload failed, keeping previous banners: " + e.getMessage());
            sender.sendMessage("Banners reload failed, previous banners kept: " + e.getMessage());
        }
    }

    void give(CommandSender sender, String playerName, String bannerId) {
        BannerDefinition def = loaded.find(bannerId).orElse(null);
        if (def == null) {
            sender.sendMessage("Unknown banner '" + bannerId + "'");
            return;
        }
        Player target = plugin.getServer().getPlayerExact(playerName);
        if (target == null) {
            sender.sendMessage("No online player named '" + playerName + "'");
            return;
        }
        items.deliver(target, def);
        sender.sendMessage("Gave " + target.getName() + " the " + def.id() + " banner");
    }

    void progress(CommandSender sender, String targetName) {
        UUID target = targetName == null ? ownId(sender) : resolve(targetName);
        if (target == null) {
            sender.sendMessage(targetName == null ? "Name a player: /mobbanners progress <player>"
                    : "Unknown player '" + targetName + "' (use their UUID if they have not joined recently)");
            return;
        }
        BannerSet set = loaded;
        withData(sender, target, data -> {
            sender.sendMessage("Banner progress for " + target + ":");
            set.banners().values().forEach(def -> sender.sendMessage(def.id() + ": "
                    + Math.min(data.kills(def.family()), def.kills()) + "/" + def.kills()
                    + (data.isEarned(def.id()) ? " (earned)" : "")));
        });
    }

    void setKills(CommandSender sender, String targetName, String bannerId, int kills) {
        UUID target = resolve(targetName);
        if (target == null) {
            sender.sendMessage("Unknown player '" + targetName + "' (use their UUID if they have not joined recently)");
            return;
        }
        BannerSet set = loaded;
        withData(sender, target, data -> {
            var result = BannerRules.setKills(data, set, bannerId, kills);
            if (result.isEmpty()) {
                sender.sendMessage("Unknown banner '" + bannerId + "'");
                return;
            }
            store.markDirty(target);
            Player online = plugin.getServer().getPlayer(target);
            for (BannerDefinition def : result.get().newlyEarned()) {
                core.logger().info("[banners] " + target + " earned banner " + def.id());
                if (online != null) {
                    items.deliver(online, def);
                }
            }
            sender.sendMessage("Set " + target + " " + result.get().family() + " kills to " + kills + "; newly earned: "
                    + result.get().newlyEarned().stream().map(BannerDefinition::id).toList()
                    + (online == null && !result.get().newlyEarned().isEmpty() ? " (offline: use /mobbanners claim)" : ""));
        });
    }

    void claim(CommandSender sender, String bannerId) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can claim banners");
            return;
        }
        PlayerData data = store.cached(player.getUniqueId());
        if (data == null) {
            sender.sendMessage("Your banner data is not loaded; try again shortly");
            return;
        }
        switch (BannerRules.claim(data, loaded, bannerId)) {
            case UNKNOWN_BANNER -> sender.sendMessage("Unknown banner '" + bannerId + "'");
            case NOT_EARNED -> sender.sendMessage("You have not earned the " + bannerId + " banner yet");
            case OK -> {
                items.deliver(player, loaded.find(bannerId).orElseThrow());
                sender.sendMessage("Here is your " + bannerId + " banner");
            }
        }
    }

    /** Loads the player's data off the main thread, then runs the action back on it. */
    private void withData(CommandSender sender, UUID id, java.util.function.Consumer<PlayerData> action) {
        store.load(id).whenCompleteAsync((data, error) -> {
            if (error != null) {
                sender.sendMessage("Could not load data for " + id + ": " + error.getMessage());
            } else {
                action.accept(data);
            }
        }, mainThread);
    }

    private UUID ownId(CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId() : null;
    }

    /** A UUID as written, an online player, or an offline player the server remembers by name. */
    private UUID resolve(String name) {
        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException ignored) {
            // not a UUID; try names
        }
        Player online = plugin.getServer().getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer offline = plugin.getServer().getOfflinePlayerIfCached(name);
        return offline == null ? null : offline.getUniqueId();
    }

    private void load() throws IOException {
        BannerSet set = BannerConfigParser.parse(core.config().load(id()), vocabulary);
        set.problems().forEach(p -> core.logger().warning("[banners] " + p));
        loaded = set;
        int built = 0;
        for (BannerDefinition def : set.banners().values()) {
            try {
                items.create(def); // proves the colour and patterns build on this server
                built++;
            } catch (RuntimeException e) {
                core.logger().warning("[banners] Banner " + def.id() + " item could not be built: " + e);
            }
        }
        core.logger().info("Banner items built: " + built + " of " + set.banners().size());
        core.logger().info("Banners loaded: " + set.banners().size() + " banners, "
                + set.families().size() + " families, " + set.problems().size() + " problems");
    }
}
