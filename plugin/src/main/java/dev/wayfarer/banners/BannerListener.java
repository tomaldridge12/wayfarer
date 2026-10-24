package dev.wayfarer.banners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Boss loot, plaque redraws and quitting players. */
final class BannerListener implements Listener {
    private final BannersModule module;

    BannerListener(BannersModule module) {
        this.module = module;
    }

    @EventHandler
    void onBossDefeated(dev.wayfarer.core.BossDefeatedEvent event) {
        module.onBossDefeated(event);
    }

    @EventHandler
    void onQuit(PlayerQuitEvent event) {
        module.playerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler
    void onProgressChanged(dev.wayfarer.core.progression.ProgressChangedEvent event) {
        // Plaques show kill counts, so mining and the like (which fire this often) need no redraw.
        if (event.raised().stream().anyMatch(a -> a.startsWith("kill:") || a.startsWith("defeat:"))) {
            module.progressChanged(event.player());
        }
    }
}
