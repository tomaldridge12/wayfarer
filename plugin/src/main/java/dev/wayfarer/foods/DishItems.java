package dev.wayfarer.foods;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.ItemTagService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.consumable.ConsumeEffect;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Builds dishes (ADR 0004): a vanilla food with its own model, name, nutrition, saturation and effects, always edible,
 * and the Wayfarer id {@code dish/<id>} that counters and the Cookbook read back.
 */
final class DishItems {
    static final String KIND = "dish";
    private static final String[] ROMAN = {"", " II", " III", " IV", " V"};

    private final ItemTagService tags;
    private final Supplier<List<DishDefinition>> dishes;

    DishItems(ItemTagService tags, Supplier<List<DishDefinition>> dishes) {
        this.tags = tags;
        this.dishes = dishes;
    }

    Optional<ItemStack> make(String id) {
        return dishes.get().stream().filter(d -> d.id().equals(id)).findFirst().map(this::build);
    }

    ItemStack build(DishDefinition d) {
        ItemStack item = ItemStack.of(Material.matchMaterial(d.base()));
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", d.model()));
        item.setData(DataComponentTypes.ITEM_NAME, ItemLook.name(d.name(), d.colour()));
        item.setData(DataComponentTypes.FOOD, FoodProperties.food().nutrition(d.nutrition()).saturation(d.saturation())
                .canAlwaysEat(true).build());
        List<PotionEffect> effects = new ArrayList<>();
        for (DishDefinition.Effect e : d.effects()) {
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(e.type()));
            if (type != null) {
                effects.add(new PotionEffect(type, e.seconds() * 20, e.amplifier(), false, true, true));
            }
        }
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .addEffect(ConsumeEffect.applyStatusEffects(effects, 1.0f)).build());
        item.lore(ItemLook.lore("Dish · " + title(d.family()) + (d.rare() ? " · Rare" : ""), effectLines(d), d.colour(), d.flavour(),
                List.of("Eat any time, even when full")));
        tags.writeId(item, KIND, d.id());
        return item;
    }

    /** The dish id of an item, or empty. */
    Optional<String> idOf(ItemStack item) {
        String id = tags.readId(item);
        return id != null && id.startsWith(KIND + "/") ? Optional.of(id.substring(KIND.length() + 1)) : Optional.empty();
    }

    /** "Haste · 8 min", one line per effect. */
    static List<String> effectLines(DishDefinition d) {
        List<String> lines = new ArrayList<>();
        for (DishDefinition.Effect e : d.effects()) {
            lines.add(title(e.type()) + ROMAN[Math.min(e.amplifier(), ROMAN.length - 1)] + " · " + ItemLook.duration(e.seconds()));
        }
        return lines;
    }

    private static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return out.toString();
    }
}
