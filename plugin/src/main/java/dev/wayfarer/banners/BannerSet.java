package dev.wayfarer.banners;

import java.util.List;
import java.util.Map;

/** The valid families and banners from one load, plus a clear message for everything rejected. */
public record BannerSet(Map<String, MobFamily> families, Map<String, BannerDefinition> banners,
        CountingSettings counting, Map<String, List<Drop>> drops, List<String> problems) {

    /** A chance (0 to 1) that killing this mob type awards the banner. */
    public record Drop(String bannerId, double chance) {
    }

    public BannerSet(Map<String, MobFamily> families, Map<String, BannerDefinition> banners,
            CountingSettings counting, List<String> problems) {
        this(families, banners, counting, Map.of(), problems);
    }

    /** Every reward in config order. Only banners so far; heads and gear will add theirs here. */
    public List<Reward> rewards() {
        return banners.values().stream().map(BannerSet::asReward).toList();
    }

    public java.util.Optional<Reward> findReward(RewardKey wanted) {
        return RewardKey.BANNER.equals(wanted.type())
                ? find(wanted.id()).map(BannerSet::asReward) : java.util.Optional.empty();
    }

    private static Reward asReward(BannerDefinition def) {
        return new Reward(RewardKey.banner(def.id()), def.name(), def.family(), def.kills());
    }

    /** The drop chances for a mob type, in config order. */
    public List<Drop> dropsFor(String mobType) {
        return drops.getOrDefault(mobType.toLowerCase(java.util.Locale.ROOT), List.of());
    }

    /** The banner for a tagged id, or empty when it is not in config (such items stay but are inert). */
    public java.util.Optional<BannerDefinition> find(String id) {
        return java.util.Optional.ofNullable(banners.get(id));
    }

    /** The family a mob type belongs to, or null. */
    public String familyOf(String mobType) {
        String mob = mobType.toLowerCase(java.util.Locale.ROOT);
        return families.values().stream().filter(f -> f.mobs().contains(mob)).map(MobFamily::id)
                .findFirst().orElse(null);
    }

    /** The largest boost zone radius of any banner, 0 when there are none. */
    public int maxRadius() {
        return banners.values().stream().mapToInt(BannerDefinition::radius).max().orElse(0);
    }

    public static BannerSet empty() {
        return new BannerSet(Map.of(), Map.of(), CountingSettings.defaults(), List.of());
    }
}
