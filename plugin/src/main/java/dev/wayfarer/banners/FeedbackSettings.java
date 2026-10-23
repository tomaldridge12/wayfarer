package dev.wayfarer.banners;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** How players are told a boost is active. Edge particles and the marker effect are off unless switched on. */
public record FeedbackSettings(
        boolean actionBar,
        boolean indicator, int indicatorMax, int indicatorLifetimeTicks, int indicatorViewDistance,
        boolean edgeParticles, String particle, int particlesPerUpdate,
        boolean markerEffect, String effect) {

    public static FeedbackSettings defaults() {
        return new FeedbackSettings(true, true, 25, 20, 16, false, "HAPPY_VILLAGER", 64, false, "luck");
    }

    static FeedbackSettings parse(Object raw, Vocabulary vocabulary, List<String> problems) {
        FeedbackSettings d = defaults();
        Map<?, ?> root = raw instanceof Map<?, ?> m ? m : Map.of();
        Map<?, ?> ind = section(root, "indicator");
        Map<?, ?> edge = section(root, "edge-particles");
        Map<?, ?> marker = section(root, "marker-effect");

        String particle = String.valueOf(edge.containsKey("particle") ? edge.get("particle") : d.particle())
                .toUpperCase(Locale.ROOT);
        if (!vocabulary.isParticle(particle)) {
            problems.add("feedback.edge-particles.particle: unknown particle '" + particle + "'; using " + d.particle());
            particle = d.particle();
        }
        String effect = String.valueOf(marker.containsKey("effect") ? marker.get("effect") : d.effect())
                .toLowerCase(Locale.ROOT);
        if (!vocabulary.isEffect(effect)) {
            problems.add("feedback.marker-effect.effect: unknown effect '" + effect + "'; using " + d.effect());
            effect = d.effect();
        }
        return new FeedbackSettings(
                bool(root, "action-bar", d.actionBar()),
                bool(ind, "enabled", d.indicator()),
                positive(ind, "max-active", d.indicatorMax(), "feedback.indicator.max-active", problems),
                positive(ind, "lifetime-ticks", d.indicatorLifetimeTicks(), "feedback.indicator.lifetime-ticks", problems),
                positive(ind, "view-distance", d.indicatorViewDistance(), "feedback.indicator.view-distance", problems),
                bool(edge, "enabled", d.edgeParticles()),
                particle,
                positive(edge, "max-per-update", d.particlesPerUpdate(), "feedback.edge-particles.max-per-update", problems),
                bool(marker, "enabled", d.markerEffect()),
                effect);
    }

    private static Map<?, ?> section(Map<?, ?> root, String key) {
        return root.get(key) instanceof Map<?, ?> m ? m : Map.of();
    }

    private static boolean bool(Map<?, ?> m, String key, boolean fallback) {
        return m.get(key) instanceof Boolean b ? b : fallback;
    }

    private static int positive(Map<?, ?> m, String key, int fallback, String name, List<String> problems) {
        Object v = m.get(key);
        if (v == null) {
            return fallback;
        }
        if (v instanceof Number n && n.intValue() > 0 && n.doubleValue() == n.intValue()) {
            return n.intValue();
        }
        problems.add(name + " must be a whole number above 0; using " + fallback);
        return fallback;
    }
}
