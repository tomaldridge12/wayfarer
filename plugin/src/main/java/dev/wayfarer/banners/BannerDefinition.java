package dev.wayfarer.banners;

import java.util.List;

/** One banner as defined in config. Percentages are whole numbers: 10 means 10%. */
public record BannerDefinition(
        String id,
        String name,
        String colour,
        List<PatternLayer> patterns,
        String family,
        int kills,
        int damageBonusPercent,
        int damageReductionPercent,
        int radius) {

    public record PatternLayer(String pattern, String colour) {
    }
}
