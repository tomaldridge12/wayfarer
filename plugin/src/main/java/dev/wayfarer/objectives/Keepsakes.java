package dev.wayfarer.objectives;

import dev.wayfarer.core.ItemLook;
import dev.wayfarer.core.ItemTagService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Keepsakes: what every contributor to a server goal receives. A player head of the contributor (so it stands as their
 * own head when placed) with its own model in the hand, naming the goal, the week and their share.
 */
final class Keepsakes {
    static final String PREFIX = "keepsake:";
    private static final int GOLD = 0xF2C744;

    private final ItemTagService tags;
    private final Supplier<Map<String, ObjectiveDefinition>> goals;

    Keepsakes(ItemTagService tags, Supplier<Map<String, ObjectiveDefinition>> goals) {
        this.tags = tags;
        this.goals = goals;
    }

    /** The loot key for a keepsake: goal, week, contribution and target. */
    static String key(String goal, long weekStart, long contribution, long target) {
        return PREFIX + goal + "~" + weekStart + "~" + contribution + "~" + target;
    }

    /** The keepsake a loot key describes, for this contributor; empty when the key is not one. */
    Optional<ItemStack> make(String loot, UUID owner, String ownerName) {
        String[] parts = loot.substring(PREFIX.length()).split("~");
        if (parts.length != 4) {
            return Optional.empty();
        }
        ObjectiveDefinition g = goals.get().get(parts[0]);
        long week = Long.parseLong(parts[1]);
        long contribution = Long.parseLong(parts[2]);
        long target = Long.parseLong(parts[3]);
        String name = g == null ? "A Server Goal" : g.name();
        ItemStack item = ItemStack.of(Material.PLAYER_HEAD);
        item.setData(DataComponentTypes.ITEM_MODEL, Key.key("wayfarer", "keepsake"));
        item.setData(DataComponentTypes.CUSTOM_NAME, ItemLook.name("Keepsake: " + name, GOLD));
        item.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile().uuid(owner).name(ownerName).build());
        item.setData(DataComponentTypes.MAX_STACK_SIZE, 1);
        item.lore(ItemLook.lore("Keepsake · Week of " + ServerGoals.WEEK.format(Instant.ofEpochMilli(week)),
                List.of(g == null ? name : g.task() + ", together", "Your share: " + FeatRules.number(contribution) + " of "
                        + FeatRules.number(target) + " (" + ServerGoalRules.share(contribution, target) + "%)"), GOLD,
                "For everyone who lent a hand. Place it, and it wears your face.", List.of()));
        tags.write(item, "objectives", "keepsake");
        return Optional.of(item);
    }
}
