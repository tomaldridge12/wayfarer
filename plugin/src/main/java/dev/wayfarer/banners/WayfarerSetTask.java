package dev.wayfarer.banners;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Gives players wearing a complete Wayfarer set their extra hearts, and takes them away when a piece comes off.
 * The modifier is transient (never saved with the player), so removing the plugin leaves no trace.
 */
final class WayfarerSetTask {
    static final long PERIOD_TICKS = 10;

    private final Plugin plugin;
    private final RelicItems relics;
    private final Supplier<WayfarerSet.Settings> settings;
    private final NamespacedKey healthKey;
    private final Set<UUID> active = new HashSet<>();

    WayfarerSetTask(Plugin plugin, RelicItems relics, Supplier<WayfarerSet.Settings> settings) {
        this.plugin = plugin;
        this.relics = relics;
        this.settings = settings;
        this.healthKey = new NamespacedKey(plugin, "wayfarer_set");
    }

    void run() {
        WayfarerSet.Settings s = settings.get();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boolean complete = WayfarerSet.complete(relics.wornArmour(player), s);
            if (complete && active.add(player.getUniqueId())) {
                apply(player, s);
                player.sendActionBar(BannerText.setActive(true, s));
            } else if (!complete && active.remove(player.getUniqueId())) {
                remove(player);
                player.sendActionBar(BannerText.setActive(false, s));
            }
        }
        active.removeIf(id -> plugin.getServer().getPlayer(id) == null);
    }

    private void apply(Player player, WayfarerSet.Settings s) {
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null && s.hearts() > 0 && health.getModifier(healthKey) == null) {
            health.addTransientModifier(new AttributeModifier(healthKey, s.hearts() * 2.0,
                    AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private void remove(Player player) {
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null && health.getModifier(healthKey) != null) {
            health.removeModifier(healthKey);
            if (player.getHealth() > health.getValue()) {
                player.setHealth(health.getValue());
            }
        }
    }

    /** Takes the bonus off everyone, for module disable. */
    void clear() {
        for (UUID id : active) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                remove(player);
            }
        }
        active.clear();
    }
}
