package dev.wayfarer.banners;

import java.util.List;
import java.util.Map;

/** The valid families and banners from one load, plus a clear message for everything rejected. */
public record BannerSet(Map<String, MobFamily> families, Map<String, BannerDefinition> banners,
        CountingSettings counting, List<String> problems) {
    /** The banner for a tagged id, or empty when it is not in config (such items stay but are inert). */
    public java.util.Optional<BannerDefinition> find(String id) {
        return java.util.Optional.ofNullable(banners.get(id));
    }

    public static BannerSet empty() {
        return new BannerSet(Map.of(), Map.of(), CountingSettings.defaults(), List.of());
    }
}
