package dev.wayfarer.banners;

import dev.wayfarer.core.ItemTagService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

/** Builds banner items from definitions and reads their hidden tag back. */
final class BannerItems {
    private static final String MODULE = "banners";

    private final ItemTagService tags;
    private final Supplier<BannerSet> banners;

    BannerItems(ItemTagService tags, Supplier<BannerSet> banners) {
        this.tags = tags;
        this.banners = banners;
    }

    ItemStack create(BannerDefinition def) {
        ItemStack item = new ItemStack(Material.valueOf(def.colour().toUpperCase(Locale.ROOT) + "_BANNER"));
        item.editMeta(BannerMeta.class, meta -> {
            for (BannerDefinition.PatternLayer layer : def.patterns()) {
                meta.addPattern(new Pattern(
                        DyeColor.valueOf(layer.colour().toUpperCase(Locale.ROOT)),
                        Registry.BANNER_PATTERN.getOrThrow(NamespacedKey.minecraft(layer.pattern()))));
            }
            meta.displayName(BannerText.name(def));
            MobFamily family = banners.get().families().get(def.family());
            meta.lore(BannerText.lore(def, family == null ? List.of() : family.mobs()));
            meta.setEnchantmentGlintOverride(true); // kept in case a client draws it; 26.3 seems not to on banners
        });
        // Hide vanilla's "Lime Creeper Charge"-style pattern list; the lore says what the banner does instead.
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY,
                TooltipDisplay.tooltipDisplay().addHiddenComponents(DataComponentTypes.BANNER_PATTERNS).build());
        tag(item, def.id());
        return item;
    }

    /** Marks an item as the banner with this id. */
    void tag(ItemStack item, String bannerId) {
        tags.write(item, MODULE, bannerId);
    }

    /** The banner id this item is tagged with, whether or not config still defines it. */
    Optional<String> idOf(ItemStack item) {
        return tags.read(item, MODULE);
    }


    /** Puts the banner in the player's inventory, or drops it at their feet if it does not fit. */
    ItemStack deliver(Player player, BannerDefinition def) {
        ItemStack item = create(def);
        player.getInventory().addItem(item.clone()).values()
                .forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        return item;
    }
}
