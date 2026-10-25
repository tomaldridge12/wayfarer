package dev.wayfarer.core.progression;

import dev.wayfarer.core.AnnounceCategory;
import dev.wayfarer.core.Announcer;
import java.time.Duration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEventSource;
import net.kyori.adventure.title.Title;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Makes earning a reward a moment: a title card, a totem-style pop, a particle burst and a fanfare for the
 * earner, and a server-wide announcement whose hover shows the item. Never used for claimed copies.
 */
final class EarnMoment {
    private final Announcer announcer;

    EarnMoment(Announcer announcer) {
        this.announcer = announcer;
    }

    /**
     * @param earner online earner, or null if they are offline (then only the announcement happens)
     * @param announce whether the ladder wants the server told; the category's config switch must be on too
     * @param category what kind of earning it is, for that switch
     * @param hover what hovering the announcement shows: the item if there is one, else this; may be null
     */
    void celebrate(Player earner, String earnerName, Component rewardName, ItemStack item, boolean announce,
            AnnounceCategory category, HoverEventSource<?> hover) {
        if (earner != null) {
            earner.showTitle(Title.title(rewardName, ProgressionText.earnedSubtitle(),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(2500), Duration.ofMillis(750))));
            earner.playEffect(EntityEffect.PROTECTED_FROM_DEATH); // the totem pop, without using a totem
            Location at = earner.getLocation().add(0, 1, 0);
            earner.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 60, 0.4, 0.6, 0.4, 0.4);
            earner.getWorld().spawnParticle(Particle.END_ROD, at, 20, 0.3, 0.5, 0.3, 0.05);
            earner.playSound(earner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f);
        }
        if (announce) {
            announcer.announce(category, ProgressionText.announcement(earnerName, rewardName), item != null ? item : hover);
        }
    }
}
