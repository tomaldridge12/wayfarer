package dev.wayfarer.banners;

import dev.wayfarer.core.ItemTagService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

/**
 * Builds relic items and keeps their text in step with config (ADR 0002): an item stores only its reward and
 * earned date; enchants are set once, when it is earned or claimed.
 */
final class RelicItems {
    private static final String MODULE = "banners";
    private static final String EARNED = "earned";

    private final ItemTagService tags;
    private final Supplier<BannerSet> banners;

    RelicItems(ItemTagService tags, Supplier<BannerSet> banners) {
        this.tags = tags;
        this.banners = banners;
    }

    ItemStack create(RelicDefinition def, long earnedAtMillis) {
        ItemStack item = new ItemStack(Material.matchMaterial(def.item()));
        item.editMeta(meta -> {
            def.enchants().forEach((key, level) -> {
                Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.fromString(key));
                if (e != null) {
                    meta.addEnchant(e, level, true); // presets may sit on items vanilla tables would not offer
                }
            });
            if (meta instanceof ArmorMeta armour && def.trimPattern() != null) {
                TrimPattern pattern = Registry.TRIM_PATTERN.get(NamespacedKey.fromString(def.trimPattern()));
                TrimMaterial material = Registry.TRIM_MATERIAL.get(NamespacedKey.fromString(def.trimMaterial()));
                if (pattern != null && material != null) {
                    armour.setTrim(new ArmorTrim(material, pattern));
                }
            }
            meta.setEnchantmentGlintOverride(true);
        });
        tags.write(item, MODULE, new RewardKey(RewardKey.RELIC, def.id()).toString());
        tags.write(item, MODULE, EARNED, Long.toString(earnedAtMillis));
        render(item, def, earnedAtMillis);
        return item;
    }

    /** The family edge of the relic weapon in a player's main hand, if any. */
    List<BoostRules.Edge> weaponEdges(org.bukkit.entity.Player player) {
        return edge(player.getInventory().getItemInMainHand(), RelicDefinition.Slot.WEAPON).map(List::of).orElse(List.of());
    }

    /** The family edges of relic armour a player is wearing, and of a relic charm in the off-hand. */
    List<BoostRules.Edge> armourEdges(org.bukkit.entity.Player player) {
        var inv = player.getInventory();
        List<BoostRules.Edge> edges = new java.util.ArrayList<>();
        edge(inv.getHelmet(), RelicDefinition.Slot.HEAD).ifPresent(edges::add);
        edge(inv.getChestplate(), RelicDefinition.Slot.CHEST).ifPresent(edges::add);
        edge(inv.getLeggings(), RelicDefinition.Slot.LEGS).ifPresent(edges::add);
        edge(inv.getBoots(), RelicDefinition.Slot.FEET).ifPresent(edges::add);
        edge(inv.getItemInOffHand(), RelicDefinition.Slot.OFFHAND).ifPresent(edges::add);
        return edges;
    }

    /** Marks an arrow with the relic bow that fired it. */
    void tagProjectile(org.bukkit.entity.Entity projectile, String relicId) {
        tags.write(projectile, MODULE + "_shot", relicId);
    }

    /** The family edge carried by an arrow fired from a relic bow. */
    List<BoostRules.Edge> projectileEdges(org.bukkit.entity.Entity projectile) {
        return tags.read(projectile, MODULE + "_shot").flatMap(id -> banners.get().findRelic(id))
                .map(def -> List.of(new BoostRules.Edge(def.id(), def.family(), def.edgePercent()))).orElse(List.of());
    }

    /** A relic's edge when it is used in the slot it is meant for; inert relics give none. */
    private Optional<BoostRules.Edge> edge(ItemStack item, RelicDefinition.Slot usedAs) {
        if (item == null || item.isEmpty()) {
            return Optional.empty();
        }
        return relicId(item).flatMap(id -> banners.get().findRelic(id)).filter(def -> def.slot() == usedAs)
                .map(def -> new BoostRules.Edge(def.id(), def.family(), def.edgePercent()));
    }

    /** The relic id on an item, or empty for anything else. */
    Optional<String> relicId(ItemStack item) {
        return tags.read(item, MODULE).map(text -> RewardKey.parse(text, RewardKey.BANNER))
                .filter(k -> RewardKey.RELIC.equals(k.type())).map(RewardKey::id);
    }

    /** Redraws a relic's name and lore from the current config; true if the item is a relic. */
    boolean refresh(ItemStack item) {
        Optional<String> id = relicId(item);
        if (id.isEmpty()) {
            return false;
        }
        long earnedAt = tags.read(item, MODULE, EARNED).map(RelicItems::parseLong).orElse(0L);
        Optional<RelicDefinition> def = banners.get().findRelic(id.get());
        if (def.isPresent()) {
            render(item, def.get(), earnedAt);
        } else {
            item.editMeta(meta -> meta.lore(List.of(BannerText.inertRelic())));
        }
        return true;
    }

    private void render(ItemStack item, RelicDefinition def, long earnedAtMillis) {
        BannerSet set = banners.get();
        MobFamily family = set.families().get(def.family());
        item.editMeta(meta -> {
            meta.displayName(BannerText.themed(def.name(), BannerText.familyColour(set, def.family())));
            meta.lore(BannerText.relicLore(def, family == null ? List.of() : family.mobs(),
                    earnedAtMillis > 0 ? TrophyHeads.date(earnedAtMillis) : null));
        });
    }

    static boolean isNetheriteUpgrade(ItemStack template) {
        return template != null && template.getType().name().toLowerCase(Locale.ROOT).startsWith("netherite_upgrade");
    }

    private static long parseLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
