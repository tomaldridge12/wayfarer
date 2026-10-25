package dev.wayfarer.core;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * The creative-menu stand-in: {@code /wayfarer items} opens a chest of every item Wayfarer adds, a page per kind. A
 * client builds its creative tabs itself, so a plugin cannot add to them. Left-click takes one, shift-click a full
 * stack. For operators and anyone in creative mode. Main thread.
 */
public final class ItemBrowser implements Listener {
    public static final String PERMISSION = "wayfarer.items";
    static final int PAGE_SIZE = 45;
    private static final int ROWS = 6;
    private static final int PREVIOUS = 45;
    private static final int BACK = 49;
    private static final int NEXT = 53;

    private final ItemCatalogue catalogue;
    private final java.util.logging.Logger log;

    public ItemBrowser(ItemCatalogue catalogue, java.util.logging.Logger log) {
        this.catalogue = catalogue;
        this.log = log;
    }

    /** Whether the player may use the browser: the permission (operators) or creative mode. */
    static boolean allowed(Player player) {
        return player.hasPermission(PERMISSION) || player.getGameMode() == GameMode.CREATIVE;
    }

    /** How many pages {@code count} items take; always at least one. */
    static int pages(int count) {
        return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("items")
                .requires(s -> !(s.getSender() instanceof Player p) || allowed(p))
                .executes(ctx -> {
                    if (ctx.getSource().getSender() instanceof Player player) {
                        open(player, null);
                    } else {
                        ctx.getSource().getSender().sendMessage(summary());
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("category", StringArgumentType.word()).requires(s -> s.getSender() instanceof Player).suggests((ctx, b) -> {
                    catalogue.categories().forEach(c -> b.suggest(c.id()));
                    return b.buildFuture();
                }).executes(ctx -> {
                    Player player = (Player) ctx.getSource().getSender();
                    String id = StringArgumentType.getString(ctx, "category");
                    if (catalogue.categories().stream().noneMatch(c -> c.id().equals(id))) {
                        player.sendMessage(Component.text("No item kind '" + id + "'. /wayfarer items lists them.", NamedTextColor.RED));
                    } else {
                        open(player, id);
                    }
                    return Command.SINGLE_SUCCESS;
                }));
    }

    /** "Wayfarer items: banners 14, heads 13, ...": how many each kind holds, for the console and the smoke test. */
    String summary() {
        List<String> parts = new ArrayList<>();
        build(null).forEach((c, items) -> parts.add(c.id() + " " + items.size()));
        return "Wayfarer items: " + String.join(", ", parts);
    }

    /** Opens the cover, or straight onto a kind. */
    public void open(Player player, String category) {
        View view = new View(build(player));
        view.show(player, category);
    }

    /** Every kind's items for the viewer (null for the console, which has no player to make heads for). */
    private Map<ItemCatalogue.Category, List<ItemStack>> build(Player player) {
        Map<ItemCatalogue.Category, List<ItemStack>> out = new LinkedHashMap<>();
        for (ItemCatalogue.Category c : catalogue.categories()) {
            try {
                out.put(c, c.items().apply(player));
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "[items] Could not build the " + c.id() + " items", e);
                out.put(c, List.of());
            }
        }
        return out;
    }

    @EventHandler
    void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof View view)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!allowed(player)) {
            player.closeInventory();
            return;
        }
        view.click(player, event.getSlot(), event.getClick());
    }

    @EventHandler
    void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof View) {
            event.setCancelled(true);
        }
    }

    /** One open browser: what every kind holds for this viewer, and where they are in it. */
    private final class View implements InventoryHolder {
        private final Map<ItemCatalogue.Category, List<ItemStack>> content;
        private final List<ItemCatalogue.Category> order;
        private Inventory inventory;
        private ItemCatalogue.Category category;
        private int page;

        View(Map<ItemCatalogue.Category, List<ItemStack>> content) {
            this.content = content;
            this.order = new ArrayList<>(content.keySet());
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        void show(Player player, String categoryId) {
            category = categoryId == null ? null : order.stream().filter(c -> c.id().equals(categoryId)).findFirst().orElse(null);
            page = 0;
            inventory = org.bukkit.Bukkit.createInventory(this, ROWS * 9, title());
            fill();
            player.openInventory(inventory);
        }

        private Component title() {
            if (category == null) {
                return ItemLook.text("Wayfarer items", ItemLook.MUTED);
            }
            int total = pages(content.get(category).size());
            return Component.text().append(category.label()).append(ItemLook.text(total > 1 ? " · " + (page + 1) + "/" + total : "",
                    ItemLook.MUTED)).build();
        }

        private void fill() {
            inventory.clear();
            if (category == null) {
                for (int i = 0; i < order.size() && i < PAGE_SIZE; i++) {
                    ItemCatalogue.Category c = order.get(i);
                    int count = content.get(c).size();
                    ItemStack icon = ItemStack.of(c.icon());
                    icon.editMeta(m -> {
                        m.displayName(c.label());
                        m.lore(List.of(ItemLook.text(count + (count == 1 ? " item" : " items"), ItemLook.MUTED)));
                        m.addItemFlags(org.bukkit.inventory.ItemFlag.values());
                    });
                    inventory.setItem(coverSlot(i), icon);
                }
                inventory.setItem(BACK, hint("Pick a kind, then left-click an item to take one, or shift-click for a full stack."));
                return;
            }
            List<ItemStack> items = content.get(category);
            for (int i = 0; i < PAGE_SIZE && page * PAGE_SIZE + i < items.size(); i++) {
                inventory.setItem(i, items.get(page * PAGE_SIZE + i).clone());
            }
            int pages = pages(items.size());
            if (page > 0) {
                inventory.setItem(PREVIOUS, nav("◂ Previous page"));
            }
            if (page < pages - 1) {
                inventory.setItem(NEXT, nav("Next page ▸"));
            }
            inventory.setItem(BACK, nav("◂ All kinds"));
        }

        /** Centres the cover's icons in rows of seven. */
        private static int coverSlot(int index) {
            return 10 + (index / 7) * 9 + index % 7;
        }

        void click(Player player, int slot, ClickType click) {
            if (category == null) {
                for (int i = 0; i < order.size(); i++) {
                    if (coverSlot(i) == slot) {
                        category = order.get(i);
                        page = 0;
                        redraw(player);
                        return;
                    }
                }
                return;
            }
            if (slot == BACK) {
                category = null;
                redraw(player);
            } else if (slot == PREVIOUS && page > 0) {
                page--;
                redraw(player);
            } else if (slot == NEXT && page < pages(content.get(category).size()) - 1) {
                page++;
                redraw(player);
            } else if (slot < PAGE_SIZE) {
                take(player, slot, click);
            }
        }

        private void redraw(Player player) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.6f, 1f);
            // A new inventory carries the new title (the page number); the old one closes without a hiccup.
            inventory = org.bukkit.Bukkit.createInventory(this, ROWS * 9, title());
            fill();
            player.openInventory(inventory);
        }

        private void take(Player player, int slot, ClickType click) {
            List<ItemStack> items = content.get(category);
            int at = page * PAGE_SIZE + slot;
            if (at >= items.size() || !(click == ClickType.LEFT || click == ClickType.SHIFT_LEFT)) {
                return;
            }
            ItemStack item = items.get(at).clone();
            item.setAmount(click.isShiftClick() ? item.getMaxStackSize() : 1);
            player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
        }

        private ItemStack nav(String label) {
            ItemStack item = ItemStack.of(Material.ARROW);
            item.editMeta(m -> m.displayName(ItemLook.text(label, ItemLook.MUTED)));
            return item;
        }

        private ItemStack hint(String text) {
            ItemStack item = ItemStack.of(Material.BOOK);
            item.editMeta(m -> m.displayName(ItemLook.text(text, ItemLook.MUTED)));
            return item;
        }
    }
}
