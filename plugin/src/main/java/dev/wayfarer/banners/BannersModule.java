package dev.wayfarer.banners;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.Module;
import dev.wayfarer.core.PlayerNames;
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

/** Trophy banners: config, the family ladders and reward types core runs, placing, boosts, drops and commands. */
public final class BannersModule implements Module {
    static final String RELOAD_PERMISSION = "wayfarer.banners.reload";
    static final String GIVE_PERMISSION = "wayfarer.banners.give";
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
    private RelicListener relicListener;
    private PerkListener perkListener;
    private WayfarerSetTask setTask;
    private BukkitTask setTimer;
    private volatile WayfarerSet.Settings setSettings = WayfarerSet.Settings.defaults();
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
        load(); // throws if the file is unreadable, which skips only this module
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
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> drops.forgetStale(plugin.getServer().getCurrentTick()), SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);
        BannerCommands.build(this).forEach(sub -> core.commands().register(id(), sub));
        core.commands().alias(id(), "mobbanners", "progress", "claim"); // the old paths, now core's commands
        for (String type : List.of(RewardKey.BANNER, RewardKey.HEAD, RewardKey.RELIC)) {
            core.progression().registerRewardType(id(), type, new Rewards(type));
        }
        registerLadders();
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
        if (core != null) {
            core.progression().drop(id());
        }
        loaded = BannerSet.empty();
    }

    BannerSet banners() {
        return loaded;
    }

    /** Every reward key in config, for operator tab-completion. */
    List<String> rewardKeys() {
        return loaded.rewards().stream().map(r -> r.rewardKey().toString()).toList();
    }

    List<String> onlineNames() {
        return plugin.getServer().getOnlinePlayers().stream().map(Player::getName).toList();
    }

    // --- events (called by the listener) ---

    void playerQuit(UUID id) {
        feedback.forget(id);
    }

    void progressChanged(UUID player) {
        placed.refreshHeadsOf(player);
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
            damagers.forEach(player -> award(player, RewardKey.banner(def.id()), "drop from " + mob.getType().getKey().getKey()));
        }
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
        java.util.OptionalLong kills = core.progression().progressIfLoaded(trophy.earner(), def.get().family());
        if (kills.isEmpty()) {
            // Offline earner: show "…" now and redraw once their record has loaded.
            core.progression().load(trophy.earner()).thenRunAsync(() -> placed.refreshHeadsOf(trophy.earner()), mainThread);
        }
        return BannerText.plaque(def.get(), BannerText.familyColour(loaded, def.get().family()),
                nameOf(trophy.earner()), kills.isEmpty() ? null : kills.getAsLong());
    }

    /** Earns a reward outside a ladder (a drop), announced as banners' feedback settings say. */
    private void award(UUID player, RewardKey reward, String how) {
        core.progression().award(player, reward.toString(), how, feedbackSettings.announceEarned());
    }

    /** Hands core this config's ladders; summing counters on read means regrouped families credit past kills. */
    private void registerLadders() {
        BannerSet set = loaded;
        core.progression().setLadders(id(), BannerLadders.build(set,
                vocabulary.mobTypes(set.counting().allMobsHostileOnly()), feedbackSettings.announceEarned()));
    }

    /** One of the banners module's reward types for core: it makes the item and names it from the loaded config. */
    private final class Rewards implements dev.wayfarer.core.progression.RewardType {
        private final String type;

        Rewards(String type) {
            this.type = type;
        }

        @Override
        public ItemStack create(String id, UUID earner, String earnerName, long earnedAt) {
            BannerSet set = loaded;
            return switch (type) {
                case RewardKey.BANNER -> set.find(id).map(items::create).orElse(null);
                case RewardKey.HEAD -> set.findHead(id).map(def -> heads.create(def, earner, earnerName, earnedAt)).orElse(null);
                case RewardKey.RELIC -> set.findRelic(id).map(def -> relics.create(def, earnedAt)).orElse(null);
                default -> null;
            };
        }

        @Override
        public net.kyori.adventure.text.Component title(String id) {
            BannerSet set = loaded;
            return set.findReward(new RewardKey(type, id)).map(r -> BannerText.rewardTitle(r, set)).orElse(null);
        }

        @Override
        public net.kyori.adventure.text.Component describe(String id) {
            BannerSet set = loaded;
            return set.findReward(new RewardKey(type, id)).map(r -> BannerText.rewardName(r, set)).orElse(null);
        }
    }

    /** "banner zombie", as written in logs and progress. */
    private static String describe(Reward reward) {
        return reward.rewardKey().type() + " " + reward.rewardKey().id();
    }

    /**
     * A boss defeat's loot: every player who damaged it gets a guaranteed boss head and materials, and their own
     * Ascended and Legendary rolls. XP orbs drop once at the boss. Core counts the defeat on the boss ladders.
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
                    award(id, r.rewardKey(), "drop from " + family);
                    got.add("ascended " + r.rewardKey().id());
                });
            }
            if (rolls.legendary()) {
                java.util.Optional.ofNullable(set.legendaries().get(family))
                        .flatMap(rid -> set.findReward(new RewardKey(RewardKey.RELIC, rid))).ifPresent(r -> {
                            award(id, r.rewardKey(), "drop from " + family);
                            got.add("legendary " + r.rewardKey().id());
                        });
            }
            core.logger().info("[banners] Boss " + family + " ★" + stars + " loot for " + id + ": " + got);
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
        UUID target = PlayerNames.resolve(plugin.getServer(), targetName);
        if (target == null) {
            sender.sendMessage("Unknown player '" + targetName + "' (use their UUID if they have not joined recently)");
            return;
        }
        award(target, reward.rewardKey(), "forced drop");
        sender.sendMessage("Forced a " + describe(reward) + " drop for " + target);
    }

    // --- commands (all run on the main thread) ---

    void reload(CommandSender sender) {
        try {
            load();
            registerLadders();
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
        UUID earner = PlayerNames.resolve(plugin.getServer(), earnerName);
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

    /** One warning for placed banners (in loaded chunks) whose id config no longer defines. */
    private void warnInert() {
        var inert = placed.inertIds(loaded);
        if (!inert.isEmpty()) {
            core.logger().warning("[banners] Placed banners in loaded chunks are inert (id not in config): " + inert);
        }
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
