package dev.wayfarer.objectives;

import dev.wayfarer.core.Core;
import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.JournalPages;
import dev.wayfarer.core.Menus;
import dev.wayfarer.core.Wayfinding;
import dev.wayfarer.core.progression.Progression;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * The tokens shop (a Journal page): what a player may buy, a confirmation before each purchase, and the purchase
 * itself with its ledger line; and the Wardrobe page, where a player wears one title and one trail of those they own.
 * Main thread.
 */
final class Shop {
    private static final int FADED = 0xB5AD9C;
    private static final int TEAL = 0x5FC9B5;

    private final Core core;
    private final ObjectivesService service;
    private final Loot loot;
    private final Supplier<ObjectivesService.State> pools;
    private final Supplier<ShopRules.Prices> prices;
    private final Supplier<Map<String, Cosmetic>> cosmetics;
    private final JournalPages journal;
    private final CosmeticsDisplay display;

    Shop(Core core, ObjectivesService service, Loot loot, Supplier<ObjectivesService.State> pools, Supplier<ShopRules.Prices> prices,
            Supplier<Map<String, Cosmetic>> cosmetics, JournalPages journal, CosmeticsDisplay display) {
        this.core = core;
        this.service = service;
        this.loot = loot;
        this.pools = pools;
        this.prices = prices;
        this.cosmetics = cosmetics;
        this.journal = journal;
        this.display = display;
    }

    JournalPages.Page shopPage() {
        return new JournalPages.Page("shop", ItemLook.text("Shop", TEAL), ItemLook.text("Spend tokens on looks and conveniences. Never power.",
                ObjectivesService.INK), 30, p -> open(p, null));
    }

    JournalPages.Page wardrobePage() {
        return new JournalPages.Page("wardrobe", ItemLook.text("Wardrobe", 0xC9B8E8), ItemLook.text("Wear a title and a trail you own.",
                ObjectivesService.INK), 31, this::wardrobe);
    }

    /** Cosmetics the player owns: bought or rolled, and earned from feats. */
    Set<String> owned(UUID id, ObjectivesPlayerData data) {
        Set<String> out = new LinkedHashSet<>(data.cosmetics);
        core.progression().snapshot(id).ifPresent(s -> s.earned().keySet().stream().filter(k -> k.startsWith("cosmetic:"))
                .forEach(k -> out.add(k.substring(9))));
        return out;
    }

    /** Enchants unlocked by any reward: rolled from tables, or earned (feats, collections). */
    Map<String, Integer> unlocked(ObjectivesPlayerData data, Progression.Snapshot snap) {
        Map<String, Integer> out = new LinkedHashMap<>(data.unlockedEnchants);
        snap.earned().keySet().stream().filter(k -> k.startsWith("enchant:")).forEach(k -> {
            ObjectivesPlayerData tmp = new ObjectivesPlayerData();
            Loot.unlock(tmp, k);
            tmp.unlockedEnchants.forEach((e, l) -> out.merge(e, l, Math::max));
        });
        return out;
    }

    /** Templates the player has owned: mark templates they earned, alloy templates seen in their inventory. */
    Set<String> templates(Player player, ObjectivesPlayerData data, Progression.Snapshot snap) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && !stack.isEmpty()) {
                core.itemTags().read(stack, "alloys").filter(t -> t.startsWith("template:"))
                        .ifPresent(t -> data.ownedTemplates.add("alloy:" + t));
            }
        }
        Set<String> out = new LinkedHashSet<>(data.ownedTemplates);
        snap.earned().keySet().stream().filter(k -> k.startsWith("mark:")).forEach(out::add);
        return out;
    }

    private ShopRules.Buyer buyer(Player player, ObjectivesPlayerData data, Progression.Snapshot snap) {
        long today = pools.get().settings().schedule().day(Instant.now());
        return new ShopRules.Buyer(data.tokens, owned(player.getUniqueId(), data), data.shopRerollDay == today,
                templates(player, data, snap), unlocked(data, snap), core.services().get(Wayfinding.class).isPresent());
    }

    private Map<String, String> templateNames(Set<String> templates) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String t : templates) {
            out.put(t, t.startsWith("alloy:template:") ? ShopRules.CollectionWords.title(t.substring(15)) + " Template"
                    : PlainTextComponentSerializer.plainText().serialize(core.progression().rewardTitle(t)) + " Template");
        }
        return out;
    }

    void open(Player player, String section) {
        service.with(player.getUniqueId(), (data, snap) -> {
            ShopRules.Buyer buyer = buyer(player, data, snap);
            List<ShopRules.Offer> offers = ShopRules.offers(prices.get(), buyer, cosmetics.get(), templateNames(buyer.ownedTemplates()));
            List<String> sections = offers.stream().map(ShopRules.Offer::section).distinct().toList();
            String shown = section != null && sections.contains(section) ? section : sections.isEmpty() ? "" : sections.getFirst();
            List<DialogBody> body = new ArrayList<>();
            body.add(Menus.text(Component.text("Tokens buy looks and small conveniences here. Never power: relics, alloys, marks and"
                    + " curios are only ever earned.", TextColor.color(FADED)).decoration(TextDecoration.ITALIC, true)));
            body.add(Menus.text(ItemLook.text("✦ " + data.tokens + " tokens", ObjectivesService.GOLD)));
            List<Menus.Button> buttons = new ArrayList<>();
            for (String s : sections) {
                buttons.add(new Menus.Button(ItemLook.text(s, s.equals(shown) ? TEAL : FADED), Component.empty(), 90, p -> open(p, s)));
            }
            for (ShopRules.Offer o : offers) {
                if (!o.section().equals(shown)) {
                    continue;
                }
                Component tooltip = ItemLook.text(o.buyable() ? "Buy for " + o.price() + " tokens" : o.blocked(), ObjectivesService.INK);
                buttons.add(new Menus.Button(ItemLook.text(o.label() + " · " + o.price(), o.buyable() ? ObjectivesService.PAPER : FADED),
                        tooltip, 180, p -> {
                            if (o.buyable()) {
                                confirm(p, o, shown);
                            } else {
                                open(p, shown);
                            }
                        }));
            }
            Menus.show(player, ItemLook.text("Shop", TEAL), body, buttons, 2, journal.back());
        });
    }

    private void confirm(Player player, ShopRules.Offer o, String section) {
        Menus.show(player, ItemLook.text("Buy " + o.label() + "?", TEAL),
                List.of(Menus.text(ItemLook.text("It costs " + o.price() + " tokens.", ObjectivesService.PAPER))),
                List.of(new Menus.Button(ItemLook.text("Buy it", ObjectivesService.DONE), Component.empty(), 120, p -> {
                    buy(p, o.id());
                    open(p, section);
                })), 1, new Menus.Button(ItemLook.text("Not now", ObjectivesService.INK), Component.empty(), 120, p -> open(p, section)));
    }

    /** Buys an offer if the player still may: tokens off (with a ledger line), then the goods. */
    void buy(Player player, String id) {
        service.with(player.getUniqueId(), (data, snap) -> {
            ShopRules.Buyer buyer = buyer(player, data, snap);
            Optional<ShopRules.Offer> offer = ShopRules.offers(prices.get(), buyer, cosmetics.get(), templateNames(buyer.ownedTemplates()))
                    .stream().filter(o -> o.id().equals(id)).findFirst();
            if (offer.isEmpty() || !offer.get().buyable()) {
                player.sendMessage(ItemLook.text("That can't be bought right now.", ObjectivesService.INK));
                return;
            }
            if (!service.tokens(player.getUniqueId(), data, -offer.get().price(), "shop " + id)) {
                return;
            }
            if (id.equals("wayfinder") && !core.services().get(Wayfinding.class).map(w -> w.offerFree(player)).orElse(false)) {
                service.tokens(player.getUniqueId(), data, offer.get().price(), "shop refund " + id);
                player.sendMessage(ItemLook.text("The Wayfinder can't point anywhere right now; your tokens are back.", ObjectivesService.INK));
                return;
            }
            switch (id) {
                case "reroll" -> data.shopRerollDay = pools.get().settings().schedule().day(Instant.now());
                case "wayfinder" -> { }
                case "kindling" -> give(player, core.itemMakers().make("exploration:beacon_kindling"));
                default -> {
                    if (id.startsWith("template:mark:")) {
                        give(player, core.progression().rewardItem(id.substring(9), player.getUniqueId()));
                    } else if (id.startsWith("template:")) {
                        give(player, core.itemMakers().make(id.substring(9)));
                    } else {
                        loot.give(player.getUniqueId(), data, List.of(id), "shop");
                    }
                }
            }
            service.store().markDirty(player.getUniqueId());
            display.refresh(player);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
            player.sendMessage(ItemLook.text("✦ Bought: " + offer.get().label() + " · " + data.tokens + " tokens left", TEAL));
        });
    }

    private static void give(Player player, Optional<ItemStack> item) {
        item.ifPresent(i -> player.getInventory().addItem(i).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left)));
    }

    // --- the wardrobe ---

    void wardrobe(Player player) {
        service.with(player.getUniqueId(), (data, snap) -> {
            Set<String> owned = owned(player.getUniqueId(), data);
            List<DialogBody> body = new ArrayList<>();
            body.add(Menus.text(Component.text("Titles show before your name in chat and the player list; trails follow you as you walk.",
                    TextColor.color(FADED)).decoration(TextDecoration.ITALIC, true)));
            body.add(Menus.text(ItemLook.text("Wearing: ", ObjectivesService.INK).append(data.title == null ? ItemLook.text("no title", FADED)
                    : display.title(data.title)).append(ItemLook.text(" · ", ObjectivesService.INK))
                    .append(ItemLook.text(data.trail == null || !cosmetics.get().containsKey(data.trail) ? "no trail"
                            : cosmetics.get().get(data.trail).name(), 0xC9B8E8))));
            List<Menus.Button> buttons = new ArrayList<>();
            for (Cosmetic c : cosmetics.get().values()) {
                if (!owned.contains(c.id())) {
                    continue;
                }
                boolean worn = c.id().equals(c.title() ? data.title : data.trail);
                buttons.add(new Menus.Button(c.title() ? display.title(c.id()) : ItemLook.text(c.name(), 0xC9B8E8),
                        ItemLook.text(worn ? "Take it off" : "Wear it", ObjectivesService.INK), 150, p -> {
                            if (c.title()) {
                                data.title = worn ? null : c.id();
                            } else {
                                data.trail = worn ? null : c.id();
                            }
                            service.store().markDirty(p.getUniqueId());
                            display.refresh(p);
                            wardrobe(p);
                        }));
            }
            if (buttons.isEmpty()) {
                body.add(Menus.text(ItemLook.text("Nothing yet. Feats pay some; the shop sells others.", FADED)));
            }
            Menus.show(player, ItemLook.text("Wardrobe", 0xC9B8E8), body, buttons, 2, journal.back());
        });
    }
}
