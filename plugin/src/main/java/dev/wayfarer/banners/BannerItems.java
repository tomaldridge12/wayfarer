package dev.wayfarer.banners;

import dev.wayfarer.core.ItemTagService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.Pattern;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

/** Builds banner items from definitions and reads their hidden tag back. */
final class BannerItems {
    private static final String MODULE = "banners";

    private final ItemTagService tags;

    BannerItems(ItemTagService tags) {
        this.tags = tags;
    }

    ItemStack create(BannerDefinition def) {
        ItemStack item = new ItemStack(Material.valueOf(def.colour().toUpperCase(Locale.ROOT) + "_BANNER"));
        item.editMeta(BannerMeta.class, meta -> {
            for (BannerDefinition.PatternLayer layer : def.patterns()) {
                meta.addPattern(new Pattern(
                        DyeColor.valueOf(layer.colour().toUpperCase(Locale.ROOT)),
                        Registry.BANNER_PATTERN.getOrThrow(NamespacedKey.minecraft(layer.pattern()))));
            }
            meta.displayName(Component.text(def.name(), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    line("+" + def.damageBonusPercent() + "% damage dealt to the " + def.family() + " family"),
                    line("-" + def.damageReductionPercent() + "% damage taken from the " + def.family() + " family"),
                    line("Radius " + def.radius() + " blocks when placed")));
            meta.setEnchantmentGlintOverride(true);
        });
        tags.write(item, MODULE, def.id());
        return item;
    }

    /** The banner id this item is tagged with, whether or not config still defines it. */
    Optional<String> idOf(ItemStack item) {
        return tags.read(item, MODULE);
    }

    private static Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
