package dev.wayfarer.core.progression;

import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;

/** How a module's kind of reward (banner, head, relic) becomes an item and is named. Main thread. */
public interface RewardType {
    /** The item for a reward id as its earner receives it, or null if config no longer has it. */
    ItemStack create(String id, UUID earner, String earnerName, long earnedAt);

    /** The reward's themed name for titles and announcements, or null if config no longer has it. */
    Component title(String id);

    /** What hovering this reward's announcement shows when it has no item (a feat's story and prizes); null for none. */
    default net.kyori.adventure.text.event.HoverEventSource<?> announceHover(String id) {
        return null;
    }

    /** The name shown on the progress card; hovering it may show the item's tooltip. */
    default Component describe(String id) {
        return title(id);
    }
}
