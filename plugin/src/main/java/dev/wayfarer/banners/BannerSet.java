package dev.wayfarer.banners;

import java.util.List;
import java.util.Map;

/** The valid families and banners from one load, plus a clear message for everything rejected. */
public record BannerSet(Map<String, MobFamily> families, Map<String, BannerDefinition> banners, List<String> problems) {
    public static BannerSet empty() {
        return new BannerSet(Map.of(), Map.of(), List.of());
    }
}
