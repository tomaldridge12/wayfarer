package dev.wayfarer.collections;

import dev.wayfarer.core.CurioBoosts;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.ItemTagService;
import dev.wayfarer.core.PlayerStore;
import dev.wayfarer.core.SuspiciousBrushEvent;
import dev.wayfarer.core.Wayfinding;
import dev.wayfarer.core.progression.Progression;
import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.BrushableBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Treasures on the server: their items, the roll at the first brush of an unrolled suspicious block at a site (with
 * per-player pity), treasures added to structure chests as they fill, and charts used up as one-use Wayfinders.
 * Main thread.
 */
final class Treasures implements Listener {
    static final String KIND = "treasure";

    /** Settings and treasures as last loaded. */
    record State(TreasureConfigParser.Settings settings, List<TreasureDefinition> treasures) {
        static State empty() {
            return new State(new TreasureConfigParser.Settings(0.12, 600, java.util.Map.of()), List.of());
        }

        Optional<TreasureDefinition> treasure(String id) {
            return treasures.stream().filter(t -> t.id().equals(id)).findFirst();
        }
    }

    private final ItemTagService tags;
    private final Supplier<State> state;
    private final PlayerStore<CollectionPlayerData> store;
    private final Progression progression;
    private final Supplier<Optional<CurioBoosts>> boosts;
    private final Supplier<Optional<Wayfinding>> wayfinding;
    private final Logger log;

    Treasures(ItemTagService tags, Supplier<State> state, PlayerStore<CollectionPlayerData> store, Progression progression,
            Supplier<Optional<CurioBoosts>> boosts, Supplier<Optional<Wayfinding>> wayfinding, Logger log) {
        this.tags = tags;
        this.state = state;
        this.store = store;
        this.progression = progression;
        this.boosts = boosts;
        this.wayfinding = wayfinding;
        this.log = log;
    }

    ItemStack item(TreasureDefinition t) {
        ItemStack item = ItemStack.of(Material.BRICK);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", t.model()));
        item.setData(DataComponentTypes.ITEM_NAME, ItemLook.name(t.name(), t.colour()));
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 16);
        List<String> footers = t.chart() != null ? List.of("Right-click to follow it, once") : List.of();
        item.lore(ItemLook.lore("Treasure · " + CollectionRules.title(t.kind()), List.of(), t.colour(), t.flavour(), footers));
        tags.writeId(item, KIND, t.id());
        return item;
    }

    Optional<ItemStack> make(String id) {
        return state.get().treasure(id).map(this::item);
    }

    @EventHandler
    void onBrush(SuspiciousBrushEvent event) {
        State s = state.get();
        if (!(event.block().getState(false) instanceof BrushableBlock brushable) || brushable.getLootTable() == null) {
            return;
        }
        String site = TreasureRules.site(s.settings().sites(), brushable.getLootTable().getKey().getKey());
        Player player = event.player();
        CollectionPlayerData data = store.cached(player.getUniqueId());
        if (site == null || data == null) {
            return;
        }
        double boost = boosts.get().map(b -> b.factor(player.getUniqueId(), "treasure")).orElse(1.0);
        TreasureRules.Brush result = TreasureRules.brush(data.treasurePity, s.settings().brushChance(), boost,
                ThreadLocalRandom.current().nextDouble());
        TreasureDefinition picked = result.treasure()
                ? TreasureRules.pick(s.treasures(), site, found(player), ThreadLocalRandom.current()) : null;
        data.treasurePity = picked == null && result.treasure() ? data.treasurePity : result.pity();
        store.markDirty(player.getUniqueId());
        if (picked == null) {
            return;
        }
        brushable.setLootTable(null);
        brushable.setItem(item(picked));
        brushable.update();
        log.info("[collections] " + player.getName() + "'s brush at a " + site + " holds a " + picked.id()
                + (boost > 1 ? " (boosted)" : ""));
    }

    /** The treasures the player has held. */
    private Set<String> found(Player player) {
        Set<String> out = new HashSet<>();
        String prefix = "find:wayfarer:" + KIND + "/";
        progression.snapshot(player.getUniqueId()).ifPresent(snap -> snap.counters().forEach((k, v) -> {
            if (v > 0 && k.startsWith(prefix)) {
                out.add(k.substring(prefix.length()));
            }
        }));
        return out;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onChest(LootGenerateEvent event) {
        String table = event.getLootTable().getKey().getKey();
        List<TreasureDefinition> found = TreasureRules.chest(state.get().treasures(), table, ThreadLocalRandom.current());
        if (found.isEmpty()) {
            return;
        }
        List<ItemStack> loot = new java.util.ArrayList<>(event.getLoot());
        found.forEach(t -> loot.add(item(t)));
        event.setLoot(loot);
        log.info("[collections] A " + table + " chest holds " + found.stream().map(TreasureDefinition::id).toList());
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onChart(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
        String id = tags.readId(held);
        TreasureDefinition chart = id == null || !id.startsWith(KIND + "/") ? null
                : state.get().treasure(id.substring(KIND.length() + 1)).filter(t -> t.chart() != null).orElse(null);
        if (chart == null || (event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()
                && !event.getPlayer().isSneaking())) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        Optional<Wayfinding> service = wayfinding.get();
        if (service.isEmpty()) {
            player.sendActionBar(ItemLook.text("The chart's markings mean nothing yet. (Wayfinding is switched off.)", Journal.FADED));
            return;
        }
        player.sendActionBar(ItemLook.text("You turn the chart this way and that...", Journal.FADED));
        service.get().pointToStructure(player, chart.chart(), state.get().settings().chartSeconds(), found -> {
            ItemStack now = player.getInventory().getItemInMainHand();
            if (!found) {
                player.sendActionBar(ItemLook.text("The chart's coastline matches nothing near here.", Journal.FADED));
                return;
            }
            if (now.isSimilar(held)) {
                now.setAmount(now.getAmount() - 1);
            }
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
            log.info("[collections] " + player.getName() + " followed a " + chart.id());
        });
    }
}
