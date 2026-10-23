package dev.wayfarer.banners;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/** Tells players a boost is active: action bar on zone changes, the ▲ on boosted hits, optional particles and effect. */
final class BannerFeedback {
    static final long UPDATE_TICKS = 10;
    private static final double RING_SPACING = 1.5;

    private final JavaPlugin plugin;
    private final PlacedBanners placed;
    private final Supplier<BannerSet> banners;
    private final Supplier<FeedbackSettings> settings;
    private final ZoneTracker tracker = new ZoneTracker();
    private final Set<TextDisplay> indicators = new HashSet<>();
    private FeedbackSettings budgetSettings;
    private IndicatorBudget budget;

    BannerFeedback(JavaPlugin plugin, PlacedBanners placed, Supplier<BannerSet> banners,
            Supplier<FeedbackSettings> settings) {
        this.plugin = plugin;
        this.placed = placed;
        this.banners = banners;
        this.settings = settings;
    }

    /** Runs every {@link #UPDATE_TICKS} on the main thread. */
    void update() {
        FeedbackSettings s = settings.get();
        BannerSet set = banners.get();
        int particleBudget = s.particlesPerUpdate();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            BoostRules.Point at = point(player);
            Map<String, BannerDefinition> zones = ZoneTracker.zonesAt(at,
                    placed.registry().within(at.world(), at.x(), at.z(), set.maxRadius()), set);
            List<ZoneTracker.Transition> changes = tracker.update(player.getUniqueId(), zones);
            if (s.actionBar() && !changes.isEmpty()) {
                List<String> parts = changes.stream().map(ZoneTracker::message).toList();
                boolean entered = changes.getLast().change() == ZoneTracker.Change.ENTER;
                player.sendActionBar(Component.text(String.join("  |  ", parts),
                        entered ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            }
            if (s.markerEffect() && !zones.isEmpty()) {
                PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(s.effect()));
                if (type != null) {
                    // Short and refreshed each update, so it fades soon after leaving the zone.
                    player.addPotionEffect(new PotionEffect(type, (int) UPDATE_TICKS * 3, 0, true, false, true));
                }
            }
            if (s.edgeParticles() && particleBudget > 0) {
                particleBudget = rings(player, at, set, s, particleBudget);
            }
        }
    }

    /** Draws each nearby zone's edge at the player's height; returns the particle budget left. */
    private int rings(Player player, BoostRules.Point at, BannerSet set, FeedbackSettings s, int budgetLeft) {
        Particle particle = Particle.valueOf(s.particle().toUpperCase(Locale.ROOT));
        List<PlacedBannerRegistry.Placed> near = new ArrayList<>(
                placed.registry().within(at.world(), at.x(), at.z(), set.maxRadius() + 8).toList());
        for (PlacedBannerRegistry.Placed p : near) {
            BannerDefinition def = set.find(p.bannerId()).orElse(null);
            if (def == null) {
                continue;
            }
            double dy = at.y() - (p.pos().y() + 0.5);
            double r2 = (double) def.radius() * def.radius() - dy * dy;
            if (r2 <= 0) {
                continue;
            }
            double r = Math.sqrt(r2);
            int points = Math.max(8, (int) (2 * Math.PI * r / RING_SPACING));
            for (int i = 0; i < points && budgetLeft > 0; i++, budgetLeft--) {
                double angle = 2 * Math.PI * i / points;
                player.spawnParticle(particle, p.pos().x() + 0.5 + r * Math.cos(angle), at.y() + 0.2,
                        p.pos().z() + 0.5 + r * Math.sin(angle), 1, 0, 0, 0, 0);
            }
            if (budgetLeft <= 0) {
                break;
            }
        }
        return budgetLeft;
    }

    /** A small bold green ▲ that floats up off the mob for about a second; skipped when the cap is reached. */
    void onBoostedHit(Entity victim) {
        FeedbackSettings s = settings.get();
        if (!s.indicator() || !budget(s).tryStart(plugin.getServer().getCurrentTick())) {
            return;
        }
        int lifetime = Math.min(s.indicatorLifetimeTicks(), 59); // the longest smooth teleport the client allows
        Location start = victim.getLocation().add(0, victim.getHeight() + 0.2, 0);
        TextDisplay mark = victim.getWorld().spawn(start, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setVisibleByDefault(false); // shown only to players nearby
            d.text(Component.text("▲", NamedTextColor.GREEN, TextDecoration.BOLD));
            d.setBillboard(Display.Billboard.CENTER);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setTeleportDuration(lifetime);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(0.7f, 0.7f, 0.7f), new AxisAngle4f()));
        });
        double range2 = (double) s.indicatorViewDistance() * s.indicatorViewDistance();
        for (Player player : victim.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(start) <= range2) {
                player.showEntity(plugin, mark);
            }
        }
        indicators.add(mark);
        var scheduler = plugin.getServer().getScheduler();
        scheduler.runTaskLater(plugin, () -> mark.teleport(start.clone().add(0, 0.8, 0)), 1);
        scheduler.runTaskLater(plugin, () -> {
            mark.remove();
            indicators.remove(mark);
        }, lifetime);
    }

    void forget(UUID player) {
        tracker.forget(player);
    }

    /** Removes any ▲ still showing; for module disable. */
    void clear() {
        indicators.forEach(Entity::remove);
        indicators.clear();
    }

    private IndicatorBudget budget(FeedbackSettings s) {
        if (budget == null || s.indicatorMax() != budgetSettings.indicatorMax()
                || s.indicatorLifetimeTicks() != budgetSettings.indicatorLifetimeTicks()) {
            budget = new IndicatorBudget(s.indicatorMax(), Math.min(s.indicatorLifetimeTicks(), 59));
            budgetSettings = s;
        }
        return budget;
    }

    private static BoostRules.Point point(Player player) {
        Location at = player.getLocation();
        return new BoostRules.Point(player.getWorld().getUID(), at.getX(), at.getY(), at.getZ());
    }
}
