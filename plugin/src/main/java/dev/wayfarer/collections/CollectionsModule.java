package dev.wayfarer.collections;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.JournalPages;
import dev.wayfarer.core.Module;
import dev.wayfarer.core.PlayerStore;
import dev.wayfarer.core.RecipeGuard;
import dev.wayfarer.core.progression.Ladder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Collections: the Journal and its collection pages (Bestiary, Atlas, Cabinet, Curios), each a distinct ladder. */
public final class CollectionsModule implements Module {
    static final String ADMIN_PERMISSION = "wayfarer.collections.admin";
    static final String JOURNAL_PERMISSION = "wayfarer.journal";
    private static final String JOURNAL_TAG = "journal";

    private final JavaPlugin plugin;
    private final List<Listener> listeners = new ArrayList<>();
    private Core core;
    private volatile Journal.State state = Journal.State.empty();
    private volatile Almanac.State fish = Almanac.State.empty();
    private Almanac almanac;
    private volatile Treasures.State treasureState = Treasures.State.empty();
    private Treasures treasures;
    private Journal journal;
    private CollectionSources sources;
    private PlayerStore<CollectionPlayerData> store;
    private BukkitTask saveTask;

    public CollectionsModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "collections";
    }

    @Override
    public void enable(Core core) throws Exception {
        this.core = core;
        store = new PlayerStore<>(plugin.getDataFolder().toPath().resolve("data").resolve(id()), CollectionPlayerData.VERSION,
                new CollectionPlayerData.Codec(), core.logger(), uuid -> plugin.getServer().getPlayer(uuid) != null);
        plugin.getServer().getOnlinePlayers().forEach(p -> store.load(p.getUniqueId()));
        saveTask = plugin.getServer().getScheduler().runTaskTimer(plugin, store::flush, 1200, 1200);
        sources = new CollectionSources(plugin.getServer(), core.itemIds(), core.itemMakers(), () -> fish.species(),
                () -> treasureState.treasures());
        treasures = new Treasures(core.itemTags(), () -> treasureState, store, core.progression(),
                () -> core.services().get(dev.wayfarer.core.CurioBoosts.class),
                () -> core.services().get(dev.wayfarer.core.Wayfinding.class), core.logger());
        almanac = new Almanac(plugin.getServer(), core.itemTags(), () -> fish, store,
                () -> core.services().get(dev.wayfarer.core.Bait.class), core.logger());
        journal = new Journal(core.progression(), core.itemMakers(), () -> state, this::detail);
        // Before load, so the collections can list fish (and later treasures) by their ids.
        core.itemIds().register(id(), Almanac.KIND, () -> fish.species().stream().map(FishSpecies::id).toList());
        core.itemMakers().register(id(), Almanac.KIND, name -> almanac.make(name, "a Wayfarer"));
        core.progression().registerRewardType(id(), Almanac.KIND, new FishReward());
        core.itemIds().register(id(), Treasures.KIND, () -> treasureState.treasures().stream().map(TreasureDefinition::id).toList());
        core.itemMakers().register(id(), Treasures.KIND, treasures::make);
        core.progression().registerRewardType(id(), Treasures.KIND, new TreasureReward());
        load();
        listen(almanac);
        listen(treasures);
        listen(new RecipeGuard(i -> isJournal(i) || i != null && Treasures.KIND.equals(kindOf(i))));
        listen(new Book());
        core.services().register(id(), JournalPages.class, journal);
        core.commands().register(id(), journalCommand());
        core.commands().register(id(), adminCommand());
    }

    @Override
    public void disable() {
        if (saveTask != null) {
            saveTask.cancel();
        }
        if (store != null) {
            store.close();
        }
        listeners.forEach(HandlerList::unregisterAll);
        listeners.clear();
    }

    private void listen(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    /** Reads collections.yml, builds every collection's entries and registers their ladders; throws if unreadable. */
    private int load() throws IOException {
        CollectionConfigParser.Result r = CollectionConfigParser.parse(core.config().load(id()));
        r.problems().forEach(p -> core.logger().warning("[collections] " + p));
        CollectionDefinition almanacDef = r.collections().get("almanac");
        if (almanacDef != null) {
            FishConfigParser.Result f = FishConfigParser.parse(almanacDef.settings());
            f.problems().forEach(p -> core.logger().warning("[collections] " + p));
            fish = new Almanac.State(f.settings(), f.species());
            int built = 0;
            for (FishSpecies s : f.species()) {
                try {
                    almanac.fish(s, 1000, "a Wayfarer", 0, null);
                    built++;
                } catch (RuntimeException e) {
                    core.logger().warning("[collections] Fish " + s.id() + " could not be built: " + e.getMessage());
                }
            }
            core.logger().info("Almanac loaded: " + f.species().size() + " species (" + String.join(", ", FishRules.RARITIES.stream()
                    .map(rarity -> f.species().stream().filter(s -> s.rarity().equals(rarity)).count() + " " + rarity).toList())
                    + "), chance " + f.settings().chance() + ", " + f.problems().size() + " problems; fish built: " + built + " of "
                    + f.species().size());
        } else {
            fish = Almanac.State.empty();
        }
        CollectionDefinition treasureDef = r.collections().get("treasures");
        if (treasureDef != null) {
            TreasureConfigParser.Result t = TreasureConfigParser.parse(treasureDef.settings());
            t.problems().forEach(p -> core.logger().warning("[collections] " + p));
            treasureState = new Treasures.State(t.settings(), t.treasures());
            int built = 0;
            for (TreasureDefinition d : t.treasures()) {
                try {
                    treasures.item(d);
                    built++;
                } catch (RuntimeException e) {
                    core.logger().warning("[collections] Treasure " + d.id() + " could not be built: " + e.getMessage());
                }
            }
            core.logger().info("Treasures loaded: " + t.treasures().size() + " treasures at " + t.settings().sites().size()
                    + " brushing sites, brush chance " + t.settings().brushChance() + " (pity at "
                    + TreasureRules.ceiling(t.settings().brushChance()) + "), " + t.problems().size() + " problems; treasures built: "
                    + built + " of " + t.treasures().size());
        } else {
            treasureState = Treasures.State.empty();
        }
        List<Ladder> others = core.progression().ladders().stream().filter(l -> !l.id().startsWith("collection_")).toList();
        Map<String, List<CollectionEntry>> entries = new LinkedHashMap<>();
        List<Ladder> ladders = new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        for (CollectionDefinition def : r.collections().values()) {
            List<CollectionEntry> list = sources.entries(def, others);
            entries.put(def.id(), list);
            sizes.add(def.id() + " " + list.size());
            if (!list.isEmpty()) {
                ladders.add(CollectionRules.ladder(def, list));
            }
        }
        state = new Journal.State(r.settings(), List.copyOf(r.collections().values()), entries);
        core.progression().setLadders(id(), ladders);
        Map<String, Integer> enchants = new LinkedHashMap<>();
        r.collections().values().forEach(def -> def.milestones().forEach(m -> m.rewards().stream()
                .filter(x -> x.startsWith("enchant:")).forEach(x -> dev.wayfarer.core.RewardEnchants.addReward(x, enchants))));
        core.rewardEnchants().register(id(), enchants);
        core.logger().info("Collections loaded: " + r.collections().size() + " collections (" + String.join(", ", sizes)
                + "), " + r.problems().size() + " problems");
        return r.problems().size();
    }

    /** A found entry's own line: an Almanac species shows the heaviest one caught. */
    private String detail(Player player, CollectionEntry entry) {
        if (entry.icon().startsWith(Almanac.KIND + ":")) {
            long best = almanac.best(player.getUniqueId(), entry.key());
            return best > 0 ? "Heaviest " + FishRules.weight(best) : null;
        }
        return null;
    }

    /** A treasure as a reward. */
    private final class TreasureReward implements dev.wayfarer.core.progression.RewardType {
        @Override
        public ItemStack create(String id, java.util.UUID earner, String earnerName, long earnedAt) {
            return treasures.make(id).orElse(null);
        }

        @Override
        public net.kyori.adventure.text.Component title(String id) {
            return treasureState.treasure(id).map(t -> ItemLook.name(t.name(), t.colour())).orElse(null);
        }
    }

    /** A fish as a reward: a fresh one of the species, with its earner as the catcher. */
    private final class FishReward implements dev.wayfarer.core.progression.RewardType {
        @Override
        public ItemStack create(String id, java.util.UUID earner, String earnerName, long earnedAt) {
            return almanac.make(id, earnerName).orElse(null);
        }

        @Override
        public net.kyori.adventure.text.Component title(String id) {
            return fish.species(id).map(s -> ItemLook.name(s.name(), Almanac.colour(s.rarity()))).orElse(null);
        }
    }


    /** The Journal item: right-click opens it. */
    ItemStack journalItem() {
        ItemStack item = ItemStack.of(Material.BRICK);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", "journal"));
        item.setData(DataComponentTypes.ITEM_NAME, ItemLook.name("Wayfarer's Journal", Journal.PAPER));
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        item.lore(ItemLook.lore("Journal", List.of("Everything you have found,", "and everything you haven't."), Journal.PAPER,
                "Its pages fill themselves in. Best not to ask how.", List.of("Right-click to open")));
        core.itemTags().write(item, id(), JOURNAL_TAG);
        return item;
    }

    /** The Wayfarer id's kind of an item (fish, treasure), or null. */
    private String kindOf(ItemStack item) {
        String id = core.itemTags().readId(item);
        return id == null || !id.contains("/") ? null : id.substring(0, id.indexOf('/'));
    }

    private boolean isJournal(ItemStack item) {
        return item != null && !item.isEmpty() && core.itemTags().read(item, id()).filter(JOURNAL_TAG::equals).isPresent();
    }

    /** Opens the Journal from its item, gives it on first join, and loads and saves player data. */
    private final class Book implements Listener {
        @EventHandler
        void onPreLogin(AsyncPlayerPreLoginEvent event) {
            try {
                store.load(event.getUniqueId()).join();
            } catch (RuntimeException e) {
                core.logger().severe("[collections] Could not load data for " + event.getUniqueId() + ": " + e.getMessage());
            }
        }

        @EventHandler
        void onJoin(PlayerJoinEvent event) {
            Player player = event.getPlayer();
            CollectionPlayerData data = store.cached(player.getUniqueId());
            if (data == null || data.journalGiven || !state.settings().bookOnFirstJoin()) {
                return;
            }
            data.journalGiven = true;
            store.markDirty(player.getUniqueId());
            player.getInventory().addItem(journalItem()).values()
                    .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
            player.sendMessage(ItemLook.text("A Wayfarer's Journal is tucked into your pack. Right-click it, or type /wayfarer journal.",
                    Journal.PAPER));
            core.logger().info("[collections] Gave " + player.getName() + " their Journal");
        }

        @EventHandler
        void onQuit(PlayerQuitEvent event) {
            store.save(event.getPlayer().getUniqueId());
        }

        @EventHandler(priority = EventPriority.HIGH)
        void onUse(PlayerInteractEvent event) {
            if (event.getHand() == EquipmentSlot.HAND && (event.getAction() == Action.RIGHT_CLICK_AIR
                    || event.getAction() == Action.RIGHT_CLICK_BLOCK) && isJournal(event.getItem())
                    && (event.getClickedBlock() == null || !event.getClickedBlock().getType().isInteractable())) {
                event.setCancelled(true);
                journal.openCover(event.getPlayer());
            }
        }
    }

    /** Gives a fish (fish:carp), a treasure (treasure:amber) or the Journal itself. */
    private void give(CommandSender sender, String playerName, String item) {
        Player player = plugin.getServer().getPlayerExact(playerName);
        if (player == null) {
            sender.sendMessage("No online player named '" + playerName + "'");
            return;
        }
        ItemStack made = item.equals("journal") ? journalItem()
                : item.startsWith(Almanac.KIND + ":") ? almanac.make(item.substring(5), player.getName()).orElse(null)
                : core.itemMakers().make(item).orElse(null);
        if (made == null) {
            sender.sendMessage("Nothing is made as '" + item + "' (try fish:carp, treasure:amber or journal)");
            return;
        }
        player.getInventory().addItem(made).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        sender.sendMessage("Gave " + item + " to " + player.getName());
    }

    private LiteralArgumentBuilder<CommandSourceStack> journalCommand() {
        return Commands.literal("journal")
                .requires(s -> s.getSender().hasPermission(JOURNAL_PERMISSION))
                .executes(ctx -> {
                    if (ctx.getSource().getExecutor() instanceof Player player) {
                        journal.openCover(player);
                    } else {
                        ctx.getSource().getSender().sendMessage("The Journal opens for players only");
                    }
                    return Command.SINGLE_SUCCESS;
                });
    }

    private LiteralArgumentBuilder<CommandSourceStack> adminCommand() {
        return Commands.literal("collections")
                .requires(s -> s.getSender().hasPermission(ADMIN_PERMISSION))
                .then(Commands.literal("list").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    for (CollectionDefinition def : state.collections()) {
                        List<CollectionEntry> list = state.entries(def.id());
                        List<String> tabs = list.stream().map(CollectionEntry::tab).filter(t -> !t.isEmpty()).distinct().toList();
                        sender.sendMessage(def.title() + ": " + list.size() + " " + def.unit(list.size())
                                + (tabs.isEmpty() ? "" : " " + tabs) + ", milestones at "
                                + def.milestones().stream().map(m -> CollectionRules.threshold(m, list.size()) + "").toList());
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("give").then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            plugin.getServer().getOnlinePlayers().forEach(p -> b.suggest(p.getName()));
                            return b.buildFuture();
                        }).then(Commands.argument("item", StringArgumentType.greedyString())
                                .suggests((ctx, b) -> {
                                    fish.species().forEach(s -> b.suggest("fish:" + s.id()));
                                    core.itemIds().ids("treasure").forEach(t -> b.suggest("treasure:" + t));
                                    b.suggest("journal");
                                    return b.buildFuture();
                                }).executes(ctx -> {
                                    give(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "player"),
                                            StringArgumentType.getString(ctx, "item").trim());
                                    return Command.SINGLE_SUCCESS;
                                }))))
                .then(Commands.literal("reload").executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    try {
                        int problems = load();
                        core.rewardEnchants().check();
                        sender.sendMessage("Collections reloaded: " + state.collections().size() + " collections, "
                                + problems + " problems");
                    } catch (IOException e) {
                        sender.sendMessage("Collections reload failed, keeping the old config: " + e.getMessage());
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }
}
