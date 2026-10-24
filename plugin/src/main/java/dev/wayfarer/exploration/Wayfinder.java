package dev.wayfarer.exploration;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.GuardianSites;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.Menus;
import dev.wayfarer.core.Wayfinding;
import dev.wayfarer.core.progression.Progression;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.LodestoneTracker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BiomeSearchResult;
import org.bukkit.util.StructureSearchResult;

/**
 * The Wayfinder: sneak and right-click a cartography table with a compass to choose a structure or a land you have
 * found, pay XP, and get a compass that points to the nearest one; or a Guardian's Chart, pointing to the nearest
 * guardian you have not beaten. Searches are budgeted to one a tick. Holding one shows the distance; a used-up chart
 * shows a needle for a while. Offers {@link Wayfinding} to other modules. Main thread.
 */
final class Wayfinder implements Listener, Wayfinding {
    static final int GOLD = 0xD9A55B;
    static final int PAPER = 0xE8D9A8;
    static final int INK = 0xB8AC94;
    static final int FADED = 0x8C8475;
    private static final String TAG = "exploration";

    /** One target to search for, resolved later on its tick. */
    private record Search(Player player, List<String> structures, String biome, Consumer<Optional<Found>> done) {
    }

    /** A found place: what it is called, where, and which structure or biome it was. */
    record Found(String key, String name, Location at) {
    }

    /** A chart's needle: where it points, its name, until when. */
    private record Needle(Location at, String name, long until) {
    }

    private final JavaPlugin plugin;
    private final Core core;
    private final Supplier<ExplorationConfigParser.Settings> settings;
    private final ExplorationState state;
    private final Runnable save;
    private final Map<UUID, Long> lastSearch = new HashMap<>();
    private final Map<UUID, Needle> needles = new HashMap<>();
    private final Deque<Runnable> queue = new ArrayDeque<>();
    private BukkitTask worker;
    private BukkitTask shower;

    Wayfinder(JavaPlugin plugin, Core core, Supplier<ExplorationConfigParser.Settings> settings, ExplorationState state, Runnable save) {
        this.plugin = plugin;
        this.core = core;
        this.settings = settings;
        this.state = state;
        this.save = save;
    }

    private int charges(UUID id) {
        return state.charges.getOrDefault(id.toString(), 0);
    }

    void start() {
        worker = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Runnable next = queue.poll();
            if (next != null) {
                next.run();
            }
        }, 1, 1);
        shower = plugin.getServer().getScheduler().runTaskTimer(plugin, this::show, 10, 10);
    }

    void stop() {
        if (worker != null) {
            worker.cancel();
        }
        if (shower != null) {
            shower.cancel();
        }
        queue.clear();
        needles.clear();
    }

    // --- the cartography table ---

    @EventHandler(priority = EventPriority.HIGH)
    void onTable(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.CARTOGRAPHY_TABLE || !event.getPlayer().isSneaking()
                || event.getItem() == null || event.getItem().getType() != Material.COMPASS || isOurs(event.getItem())) {
            return;
        }
        event.setCancelled(true);
        menu(event.getPlayer(), charges(event.getPlayer().getUniqueId()) > 0);
    }

    /** A free pointing: kept as a charge until used (closing the screen keeps it for the next cartography table). */
    @Override
    public boolean offerFree(Player player) {
        state.charges.merge(player.getUniqueId().toString(), 1, Integer::sum);
        save.run();
        menu(player, true);
        return true;
    }

    /** The first screen: a structure, a land, or the Guardian's Chart. */
    void menu(Player player, boolean free) {
        long left = ExplorationRules.cooldownLeft(lastSearch.getOrDefault(player.getUniqueId(), 0L), settings.get().cooldownSeconds(),
                System.currentTimeMillis());
        List<Menus.Button> buttons = new ArrayList<>();
        buttons.add(new Menus.Button(ItemLook.text("A structure", PAPER), ItemLook.text("Villages, temples, ruins and stranger places", INK), 140,
                p -> structures(p, free)));
        buttons.add(new Menus.Button(ItemLook.text("A land you have found", PAPER), ItemLook.text("Any biome you have stood in", INK), 140,
                p -> biomes(p, free, 0)));
        if (core.services().get(GuardianSites.class).isPresent()) {
            buttons.add(new Menus.Button(ItemLook.text("Guardian's Chart", 0x5FA8D9),
                    ItemLook.text("The nearest guardian you have not beaten" + (free ? "" : " · " + settings.get().chartLevels() + " levels"), INK),
                    140, p -> chart(p, free)));
        }
        String note = free ? "This one is free (" + charges(player.getUniqueId()) + " free pointing"
                + (charges(player.getUniqueId()) == 1 ? "" : "s") + " left)." : left > 0 ? "The needle is still settling: " + (left / 1000 + 59) / 60 + " min until you can search again."
                : "Pay in experience levels; the compass in your hand becomes the Wayfinder.";
        Menus.show(player, ItemLook.text("✦ Wayfinder", GOLD), List.of(Menus.text(Component.text(
                "Tell the needle what you seek, and it will find the nearest within " + settings.get().rangeBlocks() + " blocks.",
                TextColor.color(FADED)).decoration(TextDecoration.ITALIC, true)), Menus.text(ItemLook.text(note, INK))),
                buttons, 1, new Menus.Button(ItemLook.text("Close", INK), Component.empty(), 120, Menus::close));
    }

    private void structures(Player player, boolean free) {
        List<Menus.Button> buttons = new ArrayList<>();
        settings.get().structures().forEach((key, name) -> {
            int cost = free ? 0 : settings.get().prices().of(key, true);
            buttons.add(new Menus.Button(ItemLook.text(name, PAPER), ItemLook.text(free ? "Free" : cost + " levels", INK), 110,
                    p -> buy(p, free, cost, List.of(key), null, name, "wayfinder_compass")));
        });
        Menus.show(player, ItemLook.text("A structure", GOLD), List.of(Menus.text(ItemLook.text("Which shall the needle seek?", INK))),
                buttons, 3, new Menus.Button(ItemLook.text("◂ Back", INK), Component.empty(), 120, p -> menu(p, free)));
    }

    private void biomes(Player player, boolean free, int page) {
        Optional<Progression.Snapshot> snap = core.progression().snapshot(player.getUniqueId());
        List<String> found = new ArrayList<>();
        snap.ifPresent(s -> s.counters().forEach((k, v) -> {
            if (v > 0 && k.startsWith("discover:biome/")) {
                found.add(k.substring("discover:biome/".length()));
            }
        }));
        found.sort(String::compareTo);
        int per = 24;
        int pages = Math.max(1, (found.size() + per - 1) / per);
        int at = Math.max(0, Math.min(page, pages - 1));
        List<Menus.Button> buttons = new ArrayList<>();
        for (String biome : found.subList(at * per, Math.min(found.size(), (at + 1) * per))) {
            String name = title(biome);
            int cost = free ? 0 : settings.get().prices().of(biome, false);
            buttons.add(new Menus.Button(ItemLook.text(name, PAPER), ItemLook.text(free ? "Free" : cost + " levels", INK), 110,
                    p -> buy(p, free, cost, List.of(), biome, name, "wayfinder_compass")));
        }
        if (at > 0) {
            buttons.add(new Menus.Button(ItemLook.text("◂ Turn back", INK), Component.empty(), 110, p -> biomes(p, free, at - 1)));
        }
        if (at < pages - 1) {
            buttons.add(new Menus.Button(ItemLook.text("Turn over ▸", INK), Component.empty(), 110, p -> biomes(p, free, at + 1)));
        }
        Menus.show(player, ItemLook.text("A land you have found", GOLD), List.of(Menus.text(ItemLook.text(found.isEmpty()
                ? "You haven't stood anywhere worth remembering yet." : "Which land shall the needle seek?", INK))), buttons, 3,
                new Menus.Button(ItemLook.text("◂ Back", INK), Component.empty(), 120, p -> menu(p, free)));
    }

    private void chart(Player player, boolean free) {
        Optional<GuardianSites> sites = core.services().get(GuardianSites.class);
        Map<String, Long> counters = core.progression().snapshot(player.getUniqueId()).map(Progression.Snapshot::counters).orElse(Map.of());
        List<String> targets = sites.map(s -> List.copyOf(ExplorationRules.unbeaten(s.guardians(), counters))).orElse(List.of());
        if (targets.isEmpty()) {
            Menus.close(player);
            player.sendMessage(ItemLook.text("Every guardian you know of has fallen to you. The chart stays blank.", INK));
            return;
        }
        buy(player, free, free ? 0 : settings.get().chartLevels(), targets, null, null, "guardians_chart");
    }

    /** Checks the cooldown and XP, then queues the search; pays and gives the compass only if something was found. */
    private void buy(Player player, boolean free, int cost, List<String> structures, String biome, String name, String model) {
        Menus.close(player);
        UUID id = player.getUniqueId();
        long left = ExplorationRules.cooldownLeft(lastSearch.getOrDefault(id, 0L), settings.get().cooldownSeconds(), System.currentTimeMillis());
        if (!free && left > 0) {
            player.sendMessage(ItemLook.text("The needle is still settling. Try again in " + (left / 1000 + 59) / 60 + " min.", INK));
            return;
        }
        if (!free && player.getLevel() < cost) {
            player.sendMessage(ItemLook.text("That costs " + cost + " levels; you have " + player.getLevel() + ".", INK));
            return;
        }
        if (!free && player.getInventory().getItemInMainHand().getType() != Material.COMPASS) {
            player.sendMessage(ItemLook.text("Hold a compass for the needle to wake in.", INK));
            return;
        }
        player.sendActionBar(ItemLook.text("The needle trembles, searching...", GOLD));
        player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 0.8f, 0.8f);
        search(new Search(player, structures, biome, found -> {
            if (!player.isOnline()) {
                return;
            }
            if (found.isEmpty()) {
                player.sendMessage(ItemLook.text("Nothing like that within " + settings.get().rangeBlocks() + " blocks. Nothing was spent.", INK));
                return;
            }
            if (free) {
                if (charges(id) <= 0) {
                    player.sendMessage(ItemLook.text("Your free pointing was already used.", INK));
                    return;
                }
                state.charges.merge(id.toString(), -1, Integer::sum);
                state.charges.values().removeIf(c -> c <= 0);
                save.run();
            } else {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() != Material.COMPASS || player.getLevel() < cost) {
                    player.sendMessage(ItemLook.text("You let go of the compass; the needle went still. Nothing was spent.", INK));
                    return;
                }
                player.setLevel(player.getLevel() - cost);
                held.setAmount(held.getAmount() - 1);
                lastSearch.put(id, System.currentTimeMillis());
            }
            Found f = found.get();
            String shown = name != null ? name : guardianName(f.key());
            ItemStack compass = compass(f, shown, model);
            player.getInventory().addItem(compass).values().forEach(l -> player.getWorld().dropItem(player.getLocation(), l));
            player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1f, 1.3f);
            int distance = (int) f.at().distance(player.getLocation());
            player.sendMessage(ItemLook.text("✦ The needle settles: " + shown + ", " + distance + " blocks away.", GOLD));
            core.logger().info("[exploration] " + player.getName() + " found " + f.key() + " at " + f.at().getBlockX() + ","
                    + f.at().getBlockZ() + (free ? " (free)" : " for " + cost + " levels"));
        }));
    }

    private String guardianName(String structure) {
        return core.services().get(GuardianSites.class).map(s -> s.bossName(s.guardians().getOrDefault(structure, structure)))
                .map(n -> n + "'s lair").orElse(title(structure));
    }

    /** Queues a search: one structure or the biome per tick, then the nearest of what was found. */
    private void search(Search s) {
        Map<String, Double> found = new LinkedHashMap<>();
        Map<String, Location> where = new HashMap<>();
        World world = s.player().getWorld();
        Location from = s.player().getLocation();
        int range = settings.get().rangeBlocks();
        for (String key : s.structures()) {
            queue.add(() -> {
                NamespacedKey k = NamespacedKey.fromString(key);
                Structure structure = k == null ? null : Registry.STRUCTURE.get(k);
                if (structure == null) {
                    return;
                }
                StructureSearchResult r = world.locateNearestStructure(from, structure, Math.max(1, range / 16), false);
                if (r != null && r.getLocation().distanceSquared(from) <= (double) range * range) {
                    found.put(key, r.getLocation().distance(from));
                    where.put(key, r.getLocation());
                }
            });
        }
        if (s.biome() != null) {
            queue.add(() -> {
                NamespacedKey k = NamespacedKey.fromString(s.biome().contains(":") ? s.biome() : "minecraft:" + s.biome());
                Biome biome = k == null ? null : Registry.BIOME.get(k);
                if (biome != null) {
                    BiomeSearchResult r = world.locateNearestBiome(from, range, 32, 64, biome);
                    if (r != null) {
                        found.put(s.biome(), r.getLocation().distance(from));
                        where.put(s.biome(), r.getLocation());
                    }
                }
            });
        }
        queue.add(() -> s.done().accept(ExplorationRules.nearest(found).map(key -> new Found(key, title(key), where.get(key)))));
    }

    /** A compass pointing at the place, with its own look, name and a line about where it found it. */
    ItemStack compass(Found f, String name, String model) {
        ItemStack item = ItemStack.of(Material.COMPASS);
        item.setData(DataComponentTypes.LODESTONE_TRACKER, LodestoneTracker.lodestoneTracker(f.at(), false));
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", model));
        boolean chart = model.equals("guardians_chart");
        item.setData(DataComponentTypes.CUSTOM_NAME, ItemLook.name((chart ? "Guardian's Chart · " : "Wayfinder · ") + name, chart ? 0x5FA8D9 : GOLD));
        item.lore(ItemLook.lore(chart ? "Guardian's Chart" : "Wayfinder", List.of("Points to " + name), chart ? 0x5FA8D9 : GOLD,
                chart ? "The needle shivers towards something that has not yet been beaten."
                        : "Its needle knows what you asked for, and nothing else.", List.of()));
        core.itemTags().write(item, TAG, (chart ? "chart:" : "wayfinder:") + name);
        return item;
    }

    private boolean isOurs(ItemStack item) {
        return core.itemTags().read(item, TAG).isPresent();
    }

    // --- charts and showing the way ---

    @Override
    public boolean pointToStructure(Player player, String structure, int seconds) {
        NamespacedKey k = NamespacedKey.fromString(structure);
        Structure s = k == null ? null : Registry.STRUCTURE.get(k);
        if (s == null) {
            return false;
        }
        int range = settings.get().rangeBlocks();
        StructureSearchResult r = player.getWorld().locateNearestStructure(player.getLocation(), s, Math.max(1, range / 16), false);
        if (r == null || r.getLocation().distanceSquared(player.getLocation()) > (double) range * range) {
            return false;
        }
        needles.put(player.getUniqueId(), new Needle(r.getLocation(), title(structure), System.currentTimeMillis() + seconds * 1000L));
        core.logger().info("[exploration] " + player.getName() + " follows a chart to " + structure + " at " + r.getLocation().getBlockX()
                + "," + r.getLocation().getBlockZ() + " for " + seconds + "s");
        return true;
    }

    /** Twice a second: the distance on the action bar for a held Wayfinder, and any chart's needle. */
    private void show() {
        long now = System.currentTimeMillis();
        needles.values().removeIf(n -> n.until() < now);
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            ItemStack held = p.getInventory().getItemInMainHand();
            Optional<String> tag = held.getType() == Material.COMPASS ? core.itemTags().read(held, TAG) : Optional.empty();
            if (tag.isPresent() && held.hasData(DataComponentTypes.LODESTONE_TRACKER)) {
                Location at = held.getData(DataComponentTypes.LODESTONE_TRACKER).location();
                if (at != null && at.getWorld() == p.getWorld()) {
                    p.sendActionBar(way(p, at, tag.get().substring(tag.get().indexOf(':') + 1)));
                }
                continue;
            }
            Needle n = needles.get(p.getUniqueId());
            if (n != null && n.at().getWorld() == p.getWorld()) {
                p.sendActionBar(way(p, n.at(), n.name()));
            }
        }
    }

    private static Component way(Player p, Location at, String name) {
        double dx = at.getX() - p.getLocation().getX();
        double dz = at.getZ() - p.getLocation().getZ();
        int distance = (int) Math.sqrt(dx * dx + dz * dz);
        return ItemLook.text(ExplorationRules.arrow(p.getLocation().getYaw(), dx, dz) + " ", GOLD)
                .append(ItemLook.text(name + " · " + distance + " blocks", PAPER));
    }

    static String title(String key) {
        String path = key.substring(key.indexOf(':') + 1);
        StringBuilder out = new StringBuilder();
        for (String w : path.split("_")) {
            if (!w.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
            }
        }
        return out.toString();
    }
}
