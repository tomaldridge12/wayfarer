package dev.wayfarer.curios;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.Menus;
import dev.wayfarer.core.PlayerStore;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Opening supplies by right-click: loot rolls, the animal crate's choice, bait and tomes. Main thread. */
final class Supplies implements Listener {
    private final Supplier<LootItems.Loot> loot;
    private final LootItems items;
    private final PlayerStore<CurioPlayerData> store;
    private final Logger log;

    Supplies(Supplier<LootItems.Loot> loot, LootItems items, PlayerStore<CurioPlayerData> store, Logger log) {
        this.loot = loot;
        this.items = items;
        this.store = store;
        this.log = log;
    }

    @EventHandler(priority = EventPriority.HIGH)
    void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        SupplyDefinition s = items.supplyOf(held).map(id -> loot.get().supplies().get(id)).orElse(null);
        if (s == null || (event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()
                && !player.isSneaking())) {
            return;
        }
        event.setCancelled(true);
        switch (s.opens()) {
            case "loot" -> openLoot(player, s);
            case "crate" -> crateMenu(player, s);
            case "bait" -> bait(player, s);
            case "xp" -> tome(player, s);
            default -> { }
        }
    }

    /** Takes one of this supply from the player's main hand; false if it is no longer there. */
    private boolean useOne(Player player, SupplyDefinition s) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!items.supplyOf(held).filter(s.id()::equals).isPresent()) {
            return false;
        }
        held.setAmount(held.getAmount() - 1);
        return true;
    }

    private void openLoot(Player player, SupplyDefinition s) {
        if (!useOne(player, s)) {
            return;
        }
        Map<String, Integer> rolled = SupplyRules.roll(s.loot(), s.rolls(), ThreadLocalRandom.current());
        List<String> shown = new ArrayList<>();
        rolled.forEach((key, amount) -> {
            Material m = Material.matchMaterial(key);
            if (m == null) {
                return;
            }
            give(player, ItemStack.of(m, amount));
            shown.add(amount + " " + words(key));
        });
        player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 1f, 1f);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1.2, 0), 8, 0.4, 0.3, 0.4);
        player.sendActionBar(ItemLook.text(s.name() + ": " + String.join(", ", shown), ItemLook.soft(s.colour())));
    }

    private void crateMenu(Player player, SupplyDefinition s) {
        List<Menus.Button> buttons = new ArrayList<>();
        for (String choice : s.choices()) {
            String[] parts = choice.split("/");
            buttons.add(new Menus.Button(Component.text(label(parts[0], parts[1])),
                    Component.text("A baby " + label(parts[0], parts[1]).toLowerCase(Locale.ROOT)), 100,
                    p -> {
                        Menus.close(p);
                        if (useOne(p, s)) {
                            hatch(p, parts[0], parts[1]);
                        }
                    }));
        }
        Menus.show(player, ItemLook.name(s.name(), s.colour()),
                List.of(Menus.text(Component.text("Something inside is shuffling about. Which one is it?", NamedTextColor.GRAY))),
                buttons, 4, new Menus.Button(Component.text("Keep it shut"), Component.empty(), 100, Menus::close));
    }

    private void hatch(Player player, String mob, String variant) {
        Location at = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(1.5));
        EntityType type = switch (mob) {
            case "cow" -> EntityType.COW;
            case "pig" -> EntityType.PIG;
            case "chicken" -> EntityType.CHICKEN;
            default -> EntityType.SHEEP;
        };
        player.getWorld().spawn(at, type.getEntityClass(), e -> {
            if (e instanceof Ageable baby) {
                baby.setBaby();
            }
            NamespacedKey key = NamespacedKey.minecraft(variant);
            switch (e) {
                case Cow cow -> Optional.ofNullable(RegistryAccess.registryAccess().getRegistry(RegistryKey.COW_VARIANT).get(key)).ifPresent(cow::setVariant);
                case Pig pig -> Optional.ofNullable(RegistryAccess.registryAccess().getRegistry(RegistryKey.PIG_VARIANT).get(key)).ifPresent(pig::setVariant);
                case Chicken chicken -> Optional.ofNullable(RegistryAccess.registryAccess().getRegistry(RegistryKey.CHICKEN_VARIANT).get(key)).ifPresent(chicken::setVariant);
                case Sheep sheep -> sheep.setColor(DyeColor.valueOf(variant.toUpperCase(Locale.ROOT)));
                default -> { }
            }
        });
        player.getWorld().spawnParticle(Particle.HEART, at.clone().add(0, 0.8, 0), 3, 0.3, 0.2, 0.3);
        player.playSound(at, Sound.BLOCK_BARREL_OPEN, 1f, 1.2f);
        log.info("[curios] Animal crate: a baby " + variant + " " + mob + " for " + player.getName());
    }

    private void bait(Player player, SupplyDefinition s) {
        CurioPlayerData data = store.cached(player.getUniqueId());
        if (data == null || data.bait >= SupplyRules.MAX_BAIT || !useOne(player, s)) {
            if (data != null && data.bait >= SupplyRules.MAX_BAIT) {
                player.sendActionBar(ItemLook.text("Your bait tin is full (" + SupplyRules.MAX_BAIT + " casts)", ItemLook.MUTED));
            }
            return;
        }
        data.bait = SupplyRules.addBait(data.bait, s.amount());
        store.markDirty(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.6f, 1.3f);
        player.sendActionBar(ItemLook.text("Baited: your next " + data.bait + " catches tempt the rarer fish",
                ItemLook.soft(s.colour())));
    }

    private void tome(Player player, SupplyDefinition s) {
        if (!useOne(player, s)) {
            return;
        }
        player.giveExp(s.amount(), true);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.1f);
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.4, 0), 40, 0.5, 0.6, 0.5, 0.6);
    }

    /** The Almanac's question: one baited catch used, if any. */
    double useCast(UUID player) {
        CurioPlayerData data = store.cached(player);
        if (data == null) {
            return 1;
        }
        SupplyRules.Cast cast = SupplyRules.cast(data.bait);
        if (cast.left() != data.bait) {
            data.bait = cast.left();
            store.markDirty(player);
        }
        return cast.factor();
    }

    private static void give(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
    }

    static String label(String mob, String variant) {
        return words(variant) + " " + words(mob);
    }

    static String words(String key) {
        String s = key.replace('_', ' ');
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
