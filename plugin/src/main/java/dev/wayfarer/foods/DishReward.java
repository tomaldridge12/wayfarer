package dev.wayfarer.foods;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.progression.RewardType;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

/** A dish as reward loot: {@code dish:harvest_feast} or {@code dish:harvest_feast*3} for a stack of three. */
final class DishReward implements RewardType {
    private final DishItems items;

    DishReward(DishItems items) {
        this.items = items;
    }

    @Override
    public ItemStack create(String id, UUID earner, String earnerName, long earnedAt) {
        String[] parts = id.split("\\*");
        int amount = parts.length > 1 && parts[1].matches("\\d+") ? Integer.parseInt(parts[1]) : 1;
        return items.make(parts[0], amount).orElse(null);
    }

    @Override
    public Component title(String id) {
        String[] parts = id.split("\\*");
        return items.definition(parts[0])
                .map(d -> ItemLook.name((parts.length > 1 ? parts[1] + " × " : "") + d.name(), d.colour())).orElse(null);
    }

    @Override
    public Component describe(String id) {
        String[] parts = id.split("\\*");
        Component title = title(id);
        return title == null ? null : items.make(parts[0]).map(i -> title.hoverEvent(i.asHoverEvent())).orElse(title);
    }
}
