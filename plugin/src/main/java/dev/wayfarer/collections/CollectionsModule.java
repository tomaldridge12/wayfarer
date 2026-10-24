package dev.wayfarer.collections;

import com.mojang.brigadier.Command;
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
        sources = new CollectionSources(plugin.getServer(), core.itemIds(), core.itemMakers());
        journal = new Journal(core.progression(), core.itemMakers(), () -> state);
        load();
        listen(new RecipeGuard(this::isJournal));
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
                .filter(x -> x.startsWith("enchant:")).forEach(x -> enchantOf(x, enchants))));
        core.rewardEnchants().register(id(), enchants);
        core.logger().info("Collections loaded: " + r.collections().size() + " collections (" + String.join(", ", sizes)
                + "), " + r.problems().size() + " problems");
        return r.problems().size();
    }

    /** "enchant:excellentenchants:decapitator_1" adds excellentenchants:decapitator at level 1. */
    static void enchantOf(String reward, Map<String, Integer> into) {
        String id = reward.substring("enchant:".length());
        int cut = id.lastIndexOf('_');
        if (cut > 0 && id.substring(cut + 1).matches("\\d+")) {
            into.merge(id.substring(0, cut), Integer.parseInt(id.substring(cut + 1)), Math::max);
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
