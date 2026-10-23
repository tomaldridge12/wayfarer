package dev.wayfarer.banners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Hands every entity hit to the boost glue, and boosted hits to the feedback. HIGH so most damage changes by other plugins come first. */
final class BoostListener implements Listener {
    private final BoostGlue boosts;
    private final BannerFeedback feedback;

    BoostListener(BoostGlue boosts, BannerFeedback feedback) {
        this.boosts = boosts;
        this.feedback = feedback;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void onDamage(EntityDamageByEntityEvent event) {
        boosts.apply(event).filter(applied -> applied.resolved().bonus())
                .ifPresent(applied -> feedback.onBoostedHit(event.getEntity()));
    }
}
