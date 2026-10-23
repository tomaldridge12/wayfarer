package dev.wayfarer.banners;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.Module;
import dev.wayfarer.core.PlayerStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
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
    static final String PLACE_PERMISSION = "wayfarer.banners.place";
    static final String FORCEDROP_PERMISSION = "wayfarer.banners.forcedrop";
    private static final long SAVE_INTERVAL_TICKS = 20L * 60;

    private final JavaPlugin plugin;
    private final Vocabulary vocabulary = new PaperVocabulary();
    private final Executor mainThread;
    private Core core;
    private BannerItems items;
    private TrophyHeads heads;
    private RelicItems relics;
    private EarnMoment moment;
    private RelicListener relicListener;
    private PerkListener perkListener;
    private WayfarerSetTask setTask;
    private BukkitTask setTimer;
    private volatile WayfarerSet.Settings setSettings = WayfarerSet.Settings.defaults();
    private PlayerStore<PlayerData> store;
    private BannerListener listener;
    private PlacedBanners placed;
    private PlacedBannerListener placedListener;
    private BoostListener boostListener;
    private DropListener dropListener;
    private final DropRules drops = new DropRules();
    private BannerFeedback feedback;
    private BukkitTask feedbackTask;
    private volatile FeedbackSettings feedbackSettings = FeedbackSettings.defaults();
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
        this.items = new BannerItems(core.itemTags(), this::banners);
        this.relics = new RelicItems(core.itemTags(), this::banners);
        this.moment = new EarnMoment(plugin.getServer(), () -> feedbackSettings);
        load(); // throws if the file is unreadable, which skips only this module
        store = new PlayerStore<>(plugin.getDataFolder().toPath().resolve("data").resolve(id()),
                PlayerDataCodec.VERSION, new PlayerDataCodec(), core.logger(),
                uuid -> plugin.getServer().getPlayer(uuid) != null);
        plugin.getServer().getOnlinePlayers().forEach(p -> store.load(p.getUniqueId()));
        listener = new BannerListener(this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        heads = new TrophyHeads(core.itemTags(), this::banners, plugin.getServer());
        placed = new PlacedBanners(core.chunkData(), items, core.logger(), new BannerLabels(this::labelFor),
                mainThread::execute, heads, this::nameOf);
        placedListener = new PlacedBannerListener(placed, items, heads, this::banners);
        relicListener = new RelicListener(relics);
        plugin.getServer().getPluginManager().registerEvents(relicListener, plugin);
        perkListener = new PerkListener(plugin, relics, this::banners);
        plugin.getServer().getPluginManager().registerEvents(perkListener, plugin);
        setTask = new WayfarerSetTask(plugin, relics, () -> setSettings);
        setTimer = plugin.getServer().getScheduler().runTaskTimer(plugin, setTask::run,
                WayfarerSetTask.PERIOD_TICKS, WayfarerSetTask.PERIOD_TICKS);
        // After every plugin has enabled, so ExcellentEnchants can answer.
        plugin.getServer().getScheduler().runTask(plugin,
                () -> ExcellentEnchantsCheck.run(plugin.getServer(), loaded, core.logger()));
        plugin.getServer().getPluginManager().registerEvents(placedListener, plugin);
        feedback = new BannerFeedback(plugin, placed, this::banners, () -> feedbackSettings);
        feedbackTask = plugin.getServer().getScheduler().runTaskTimer(plugin, feedback::update,
                BannerFeedback.UPDATE_TICKS, BannerFeedback.UPDATE_TICKS);
        dropListener = new DropListener(this);
        plugin.getServer().getPluginManager().registerEvents(dropListener, plugin);
        boostListener = new BoostListener(new BoostGlue(placed, this::banners, relics, () -> setSettings), feedback, this::banners);
        plugin.getServer().getPluginManager().registerEvents(boostListener, plugin);
        placed.loadAll(plugin.getServer());
        core.logger().info("Placed banner registry loaded: " + placed.registry().chunkCount() + " chunks, "
                + placed.registry().all().count() + " banners");
        warnInert();
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            store.flush();
            drops.forgetStale(plugin.getServer().getCurrentTick());
        }, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(BannerCommands.build(this)));
    }

    @Override
    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (feedbackTask != null) {
            feedbackTask.cancel();
        }
        if (setTimer != null) {
            setTimer.cancel();
        }
        if (setTask != null) {
            setTask.clear();
        }
        if (feedback != null) {
            feedback.clear();
        }
        if (listener != null) {
            HandlerList.unregisterAll(listener);
        }
        if (placedListener != null) {
            HandlerList.unregisterAll(placedListener);
        }
        if (relicListener != null) {
            HandlerList.unregisterAll(relicListener);
        }
        if (perkListener != null) {
            HandlerList.unregisterAll(perkListener);
        }
        if (boostListener != null) {
            HandlerList.unregisterAll(boostListener);
        }
        if (dropListener != null) {
            HandlerList.unregisterAll(dropListener);
        }
        if (placed != null) {
            long count = placed.registry().all().count();
            placed.clear(); // the records live in chunk data, so nothing is lost
            core.logger().info("Placed banner registry unloaded: " + count + " banners");
        }
        if (store != null) {
            store.close();
        }
        loaded = BannerSet.empty();
    }

    BannerSet banners() {
        return loaded;
    }

    /** Reward keys the sender has earned, for claim tab-completion. */
    List<String> earnedKeys(CommandSender sender) {
        PlayerData data = sender instanceof Player p ? store.cached(p.getUniqueId()) : null;
        return data == null ? List.of() : List.copyOf(data.earnedView());
    }

    /** Every reward key in config, for operator tab-completion. */
    List<String> rewardKeys() {
        return loaded.rewards().stream().map(r -> r.rewardKey().toString()).toList();
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

    void playerQuit(UUID id) {
        store.save(id);
        feedback.forget(id);
    }

    void onKill(Player killer, Kill kill) {
        PlayerData data = store.cached(killer.getUniqueId());
        if (data == null) {
            return; // data failed to load; already logged
        }
        RewardRules.KillResult result = RewardRules.recordKill(data, kill, loaded);
        if (result.counted()) {
            store.markDirty(killer.getUniqueId());
            stampEarned(data, result.newlyEarned());
            result.newlyEarned().forEach(reward -> award(killer, reward));
            placed.refreshHeadsOf(killer.getUniqueId());
        }
    }

    void recordDropHit(LivingEntity mob, UUID player) {
        if (!loaded.dropsFor(mob.getType().getKey().getKey()).isEmpty()) {
            drops.recordHit(mob.getUniqueId(), player, plugin.getServer().getCurrentTick());
        }
    }

    void onDropMobDeath(LivingEntity mob) {
        List<UUID> damagers = drops.takeDamagers(mob.getUniqueId());
        if (damagers.isEmpty()) {
            return;
        }
        List<BannerDefinition> won = DropRules.roll(mob.getType().getKey().getKey(), loaded,
                java.util.concurrent.ThreadLocalRandom.current()::nextDouble);
        for (BannerDefinition def : won) {
            Reward reward = loaded.findReward(RewardKey.banner(def.id())).orElseThrow();
            damagers.forEach(player -> awardEarned(player, reward, "drop from " + mob.getType().getKey().getKey()));
        }
    }

    /** Marks a reward earned (once) for a player, online or not, and delivers it if they are online. */
    private void awardEarned(UUID id, Reward reward, String how) {
        store.load(id).whenCompleteAsync((data, error) -> {
            if (error != null) {
                core.logger().severe("[banners] Could not award " + reward.rewardKey() + " to " + id + ": "
                        + error.getMessage());
                return;
            }
            if (!data.markEarned(reward.rewardKey())) {
                return; // awarded once per player; claim recovers lost copies
            }
            stampEarned(data, List.of(reward));
            store.markDirty(id);
            core.logger().info("[banners] " + id + " earned " + describe(reward) + " by " + how);
            deliverEarned(id, reward);
        }, mainThread);
    }

    /** Hands a player a copy of a reward and returns it. Each reward type delivers its own item. */
    private org.bukkit.inventory.ItemStack deliver(Player player, Reward reward) {
        org.bukkit.inventory.ItemStack item = build(player.getUniqueId(), player.getName(), reward);
        if (item != null) {
            player.getInventory().addItem(item.clone()).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        }
        return item;
    }

    /** The item for a reward as its earner would receive it, or null if config no longer has it. */
    private org.bukkit.inventory.ItemStack build(UUID earner, String earnerName, Reward reward) {
        PlayerData data = store.cached(earner);
        long earnedAt = data == null ? 0 : data.earnedAt(reward.rewardKey());
        String id = reward.rewardKey().id();
        return switch (reward.rewardKey().type()) {
            case RewardKey.BANNER -> loaded.find(id).map(items::create).orElse(null);
            case RewardKey.HEAD -> loaded.findHead(id).map(def -> heads.create(def, earner, earnerName, earnedAt)).orElse(null);
            case RewardKey.RELIC -> loaded.findRelic(id).map(def -> relics.create(def, earnedAt)).orElse(null);
            default -> null;
        };
    }

    /** Delivers a newly earned reward if the earner is online, and makes it a moment (title, pop, announcement). */
    private void deliverEarned(UUID earner, Reward reward) {
        Player online = plugin.getServer().getPlayer(earner);
        String name = online != null ? online.getName() : nameOf(earner);
        org.bukkit.inventory.ItemStack item = online != null ? deliver(online, reward) : build(earner, name, reward);
        moment.celebrate(online, name, BannerText.rewardTitle(reward, loaded), item);
    }

    private static void stampEarned(PlayerData data, List<Reward> rewards) {
        long now = System.currentTimeMillis();
        rewards.forEach(r -> data.setEarnedAt(r.rewardKey(), now));
    }

    /** A player's last known name, for plaques and head lore. */
    String nameOf(UUID id) {
        return id == null ? "A Wayfarer"
                : java.util.Optional.ofNullable(plugin.getServer().getOfflinePlayer(id).getName()).orElse("A Wayfarer");
    }

    /** The floating label for a placed-trophy record: a banner's name and effect, or a plaque's live count. */
    private net.kyori.adventure.text.Component labelFor(String record) {
        PlacedTrophy trophy = PlacedTrophy.parse(record);
        if (trophy.isBanner()) {
            return BannerText.label(trophy.reward().id(), loaded.find(trophy.reward().id()));
        }
        var def = loaded.findHead(trophy.reward().id());
        if (def.isEmpty() || trophy.earner() == null) {
            return BannerText.label(trophy.reward().toString(), java.util.Optional.empty());
        }
        PlayerData data = store.cached(trophy.earner());
        if (data == null) {
            // Offline earner: show "…" now and redraw once their data has loaded.
            store.load(trophy.earner()).thenRunAsync(() -> placed.refreshHeadsOf(trophy.earner()), mainThread);
        }
        return BannerText.plaque(def.get(), BannerText.familyColour(loaded, def.get().family()),
                nameOf(trophy.earner()), data == null ? null : data.kills(def.get().family()));
    }

    /** "banner zombie", as written in logs and progress. */
    private static String describe(Reward reward) {
        return reward.rewardKey().type() + " " + reward.rewardKey().id();
    }

    /**
     * A boss defeat: every player who damaged it gets +1 on the boss family (and its rewards), a guaranteed boss
     * head and materials, and their own Ascended and Legendary rolls. XP orbs drop once at the boss.
     */
    void onBossDefeated(dev.wayfarer.core.BossDefeatedEvent event) {
        BannerSet set = loaded;
        String family = event.boss().family();
        int stars = event.boss().stars();
        if (!set.isBossFamily(family)) {
            core.logger().warning("[banners] Boss family " + family + " is not in banners.yml (bosses.families); no rewards");
            return;
        }
        org.bukkit.Location at = event.location();
        int xp = set.bossLoot().xpPerStar() * stars;
        if (xp > 0) {
            at.getWorld().spawn(at, org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(xp));
        }
        String base = set.baseFamilyOf(family);
        for (UUID id : event.damagers()) {
            store.load(id).whenCompleteAsync((data, error) -> {
                if (error != null) {
                    core.logger().severe("[banners] Could not reward " + id + " for " + family + ": " + error.getMessage());
                    return;
                }
                RewardRules.KillResult result = RewardRules.recordBossKill(data, set, family);
                store.markDirty(id);
                stampEarned(data, result.newlyEarned());
                placed.refreshHeadsOf(id);
                for (Reward reward : result.newlyEarned()) {
                    core.logger().info("[banners] " + id + " earned " + describe(reward) + " by defeating " + family);
                    deliverEarned(id, reward);
                }
                Player online = plugin.getServer().getPlayer(id);
                List<String> got = new ArrayList<>();
                java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
                set.heads().values().stream().filter(h -> h.family().equals(family)).findFirst().ifPresent(h -> {
                    if (online != null) {
                        giveItem(online, heads.create(h, id, online.getName(), System.currentTimeMillis()));
                    }
                    got.add("head");
                });
                set.bossLoot().materials().forEach((item, range) -> {
                    int amount = BossLoot.materialAmount(range, stars, rnd::nextDouble);
                    org.bukkit.Material material = org.bukkit.Material.matchMaterial(item);
                    if (online != null && material != null && amount > 0) {
                        for (int left = amount; left > 0; left -= material.getMaxStackSize()) {
                            giveItem(online, new org.bukkit.inventory.ItemStack(material, Math.min(left, material.getMaxStackSize())));
                        }
                    }
                    got.add(amount + " " + item);
                });
                BossLoot.Rolls rolls = BossLoot.roll(set.bossLoot(), stars, base != null, rnd::nextDouble);
                if (rolls.ascended()) {
                    BossLoot.pickAscended(set, base, rnd::nextDouble).flatMap(def ->
                            set.findReward(new RewardKey(RewardKey.RELIC, def.id()))).ifPresent(r -> {
                        awardEarned(id, r, "drop from " + family);
                        got.add("ascended " + r.rewardKey().id());
                    });
                }
                if (rolls.legendary()) {
                    java.util.Optional.ofNullable(set.legendaries().get(family))
                            .flatMap(rid -> set.findReward(new RewardKey(RewardKey.RELIC, rid))).ifPresent(r -> {
                                awardEarned(id, r, "drop from " + family);
                                got.add("legendary " + r.rewardKey().id());
                            });
                }
                core.logger().info("[banners] Boss " + family + " ★" + stars + " loot for " + id + ": " + got);
            }, mainThread);
        }
    }

    /** Into the inventory, or dropped at the player's feet if it does not fit. */
    private static void giveItem(Player player, org.bukkit.inventory.ItemStack item) {
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    /** Operator tool: awards a reward as a successful drop would, without killing anything. */
    void forceDrop(CommandSender sender, String targetName, String rewardText) {
        Reward reward = loaded.findReward(RewardKey.parse(rewardText.trim(), RewardKey.BANNER)).orElse(null);
        if (reward == null) {
            sender.sendMessage("Unknown reward '" + rewardText + "'");
            return;
        }
        UUID target = resolve(targetName);
        if (target == null) {
            sender.sendMessage("Unknown player '" + targetName + "' (use their UUID if they have not joined recently)");
            return;
        }
        awardEarned(target, reward, "forced drop");
        sender.sendMessage("Forced a " + describe(reward) + " drop for " + target);
    }

    private void award(Player player, Reward reward) {
        core.logger().info("[banners] " + player.getUniqueId() + " earned " + describe(reward));
        deliverEarned(player.getUniqueId(), reward);
    }

    // --- commands (all run on the main thread) ---

    void reload(CommandSender sender) {
        try {
            load();
            warnInert();
            ExcellentEnchantsCheck.run(plugin.getServer(), loaded, core.logger());
            core.logger().info("Banner labels refreshed: " + placed.labels().refresh(placed.registry()));
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

    /** Operator tool: stands a banner at a position and registers it as if a player had placed it. */
    void place(CommandSender sender, String bannerId, int x, int y, int z, String worldName) {
        BannerDefinition def = loaded.find(bannerId).orElse(null);
        if (def == null) {
            sender.sendMessage("Unknown banner '" + bannerId + "'");
            return;
        }
        World world = worldName != null ? plugin.getServer().getWorld(worldName)
                : sender instanceof Player p ? p.getWorld() : plugin.getServer().getWorlds().getFirst();
        if (world == null) {
            sender.sendMessage("Unknown world '" + worldName + "'");
            return;
        }
        world.getChunkAt(x >> 4, z >> 4); // loads it if needed; an operator command, so a sync load is acceptable
        Block block = world.getBlockAt(x, y, z);
        ItemStack item = items.create(def);
        block.setType(item.getType());
        if (block.getState() instanceof Banner state && item.getItemMeta() instanceof BannerMeta meta) {
            state.setPatterns(meta.getPatterns());
            state.update();
        }
        placed.place(block, PlacedTrophy.banner(def.id()));
        sender.sendMessage("Placed banner " + def.id() + " at " + x + "," + y + "," + z + " in " + world.getName());
    }

    /** Operator tool: stands a player's trophy head at a position, as if they had placed it. */
    void placeHead(CommandSender sender, String earnerName, String headId, int x, int y, int z) {
        HeadDefinition def = loaded.findHead(headId).orElse(null);
        UUID earner = resolve(earnerName);
        if (def == null || earner == null) {
            sender.sendMessage(def == null ? "Unknown head '" + headId + "'" : "Unknown player '" + earnerName + "'");
            return;
        }
        World world = sender instanceof Player p ? p.getWorld() : plugin.getServer().getWorlds().getFirst();
        world.getChunkAt(x >> 4, z >> 4); // an operator command, so a sync load is acceptable
        Block block = world.getBlockAt(x, y, z);
        heads.stand(block, def);
        placed.place(block, PlacedTrophy.head(def.id(), earner, System.currentTimeMillis()));
        sender.sendMessage("Placed head " + def.id() + " of " + earner + " at " + x + "," + y + "," + z);
    }

    void progress(CommandSender sender, String targetName) {
        UUID target = targetName == null ? ownId(sender) : resolve(targetName);
        if (target == null) {
            sender.sendMessage(targetName == null ? "Name a player: /mobbanners progress <player>"
                    : "Unknown player '" + targetName + "' (use their UUID if they have not joined recently)");
            return;
        }
        BannerSet set = loaded;
        String name = java.util.Optional.ofNullable(plugin.getServer().getOfflinePlayer(target).getName())
                .orElse(target.toString());
        boolean own = sender instanceof Player p && p.getUniqueId().equals(target);
        withData(sender, target, data -> BannerText.progress(name, data, set, own).forEach(sender::sendMessage));
    }

    void setKills(CommandSender sender, String targetName, String familyOrBanner, int kills) {
        UUID target = resolve(targetName);
        if (target == null) {
            sender.sendMessage("Unknown player '" + targetName + "' (use their UUID if they have not joined recently)");
            return;
        }
        BannerSet set = loaded;
        withData(sender, target, data -> {
            var result = RewardRules.setKills(data, set, familyOrBanner, kills);
            if (result.isEmpty()) {
                sender.sendMessage("Unknown family or banner '" + familyOrBanner + "'");
                return;
            }
            store.markDirty(target);
            stampEarned(data, result.get().newlyEarned());
            placed.refreshHeadsOf(target);
            Player online = plugin.getServer().getPlayer(target);
            for (Reward reward : result.get().newlyEarned()) {
                core.logger().info("[banners] " + target + " earned " + describe(reward));
                deliverEarned(target, reward);
            }
            sender.sendMessage("Set " + target + " " + result.get().families().getFirst() + " kills to " + kills
                    + "; newly earned: " + result.get().newlyEarned().stream().map(BannersModule::describe).toList()
                    + (online == null && !result.get().newlyEarned().isEmpty() ? " (offline: use /mobbanners claim)" : ""));
        });
    }

    /** {@code rewardText} is "banner:zombie", or a bare "zombie" meaning the banner. */
    void claim(CommandSender sender, String rewardText) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can claim rewards");
            return;
        }
        PlayerData data = store.cached(player.getUniqueId());
        if (data == null) {
            sender.sendMessage("Your reward data is not loaded; try again shortly");
            return;
        }
        RewardKey wanted = RewardKey.parse(rewardText.trim(), RewardKey.BANNER);
        long now = System.currentTimeMillis();
        switch (RewardRules.claim(data, loaded, wanted, now)) {
            case UNKNOWN_REWARD -> sender.sendMessage("Unknown reward '" + rewardText + "'");
            case NOT_EARNED -> sender.sendMessage("You have not earned the " + wanted.id() + " " + wanted.type() + " yet");
            case COOLDOWN -> {
                long minutes = (RewardRules.claimWait(data, wanted, now) + 59_999) / 60_000;
                sender.sendMessage("You can claim another copy in " + (minutes >= 60
                        ? (minutes / 60) + "h " + (minutes % 60) + "m" : minutes + "m"));
            }
            case OK -> {
                store.markDirty(player.getUniqueId());
                Reward reward = loaded.findReward(wanted).orElseThrow();
                deliver(player, reward);
                sender.sendMessage("Here is your " + reward.name());
            }
        }
    }

    /** One warning for placed banners (in loaded chunks) whose id config no longer defines. */
    private void warnInert() {
        var inert = placed.inertIds(loaded);
        if (!inert.isEmpty()) {
            core.logger().warning("[banners] Placed banners in loaded chunks are inert (id not in config): " + inert);
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

    private static String onOff(boolean on) {
        return on ? "on" : "off";
    }

    private void load() throws IOException {
        Map<String, Object> raw = core.config().load(id());
        BannerSet set = BannerConfigParser.parse(raw, vocabulary);
        set.problems().forEach(p -> core.logger().warning("[banners] " + p));
        List<String> feedbackProblems = new ArrayList<>();
        FeedbackSettings feedbackNow = FeedbackSettings.parse(raw.get("feedback"), vocabulary, feedbackProblems);
        feedbackProblems.forEach(p -> core.logger().warning("[banners] " + p));
        WayfarerSet.Settings setNow = WayfarerSet.Settings.parse(raw.get("wayfarer-set"), feedbackProblems);
        feedbackProblems.stream().filter(p -> p.startsWith("wayfarer-set"))
                .forEach(p -> core.logger().warning("[banners] " + p));
        loaded = set;
        feedbackSettings = feedbackNow;
        setSettings = setNow;
        core.logger().info("Banner feedback: action bar " + onOff(feedbackNow.actionBar()) + ", damage numbers "
                + onOff(feedbackNow.damageNumbers()) + " (max " + feedbackNow.numbersMax() + "), edge particles "
                + onOff(feedbackNow.edgeParticles()) + ", marker effect " + onOff(feedbackNow.markerEffect())
                + " (" + feedbackNow.effect() + ")");
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
        core.logger().info("Ladder loaded: " + set.heads().size() + " trophy heads, " + set.relics().size() + " relics");
        int relicsBuilt = 0;
        for (RelicDefinition def : set.relics().values()) {
            try {
                relics.create(def, 0); // proves the item, enchants and trim exist on this server
                relicsBuilt++;
            } catch (RuntimeException e) {
                core.logger().warning("[banners] Relic " + def.id() + " item could not be built: " + e);
            }
        }
        core.logger().info("Relic items built: " + relicsBuilt + " of " + set.relics().size());
    }
}
