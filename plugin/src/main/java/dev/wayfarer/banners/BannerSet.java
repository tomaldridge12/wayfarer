package dev.wayfarer.banners;

import java.util.List;
import java.util.Map;

/** The valid families and banners from one load, plus a clear message for everything rejected. */
public record BannerSet(Map<String, MobFamily> families, Map<String, BannerDefinition> banners,
        Map<String, HeadDefinition> heads, Map<String, RelicDefinition> relics,
        CountingSettings counting, Map<String, List<Drop>> drops, List<String> problems,
        List<String> missingOptionalEnchants, Map<String, String> bossBase, BossLoot.Settings bossLoot,
        Map<String, String> legendaries) {

    /** The prefix the boost glue gives boss entities, so they count as their boss family. */
    public static final String BOSS_PREFIX = "boss:";

    public BannerSet(Map<String, MobFamily> families, Map<String, BannerDefinition> banners,
            Map<String, HeadDefinition> heads, Map<String, RelicDefinition> relics,
            CountingSettings counting, Map<String, List<Drop>> drops, List<String> problems,
            List<String> missingOptionalEnchants) {
        this(families, banners, heads, relics, counting, drops, problems, missingOptionalEnchants, Map.of(),
                BossLoot.Settings.defaults(), Map.of());
    }

    /** The ExcellentEnchants enchants relic presets give, each at the highest level any relic gives. */
    public Map<String, Integer> rewardEnchants() {
        Map<String, Integer> out = new java.util.TreeMap<>();
        relics.values().forEach(r -> r.enchants().forEach((k, level) -> {
            if (k.startsWith("excellentenchants:")) {
                out.merge(k, level, Math::max);
            }
        }));
        return out;
    }

    /** Whether this family is a boss's own family. */
    public boolean isBossFamily(String family) {
        return bossBase.containsKey(family);
    }

    /** A mini boss's base family (zombie for the Rotting Warlord), or null for other bosses and plain mobs. */
    public String baseFamilyOf(String typeOrFamily) {
        String family = typeOrFamily.startsWith(BOSS_PREFIX) ? typeOrFamily.substring(BOSS_PREFIX.length()) : typeOrFamily;
        String base = bossBase.get(family);
        return base == null || base.isEmpty() ? null : base;
    }

    /** Without the optional ExcellentEnchants plugin its enchants are left out of relics, listed here (not a problem). */
    public BannerSet(Map<String, MobFamily> families, Map<String, BannerDefinition> banners,
            Map<String, HeadDefinition> heads, Map<String, RelicDefinition> relics,
            CountingSettings counting, Map<String, List<Drop>> drops, List<String> problems) {
        this(families, banners, heads, relics, counting, drops, problems, List.of());
    }

    /** A chance (0 to 1) that killing this mob type awards the banner. */
    public record Drop(String bannerId, double chance) {
    }

    public BannerSet(Map<String, MobFamily> families, Map<String, BannerDefinition> banners,
            CountingSettings counting, Map<String, List<Drop>> drops, List<String> problems) {
        this(families, banners, Map.of(), Map.of(), counting, drops, problems);
    }

    public BannerSet(Map<String, MobFamily> families, Map<String, BannerDefinition> banners,
            CountingSettings counting, List<String> problems) {
        this(families, banners, counting, Map.of(), problems);
    }

    /** Every reward: banners, trophy heads and relics, ordered by kill count (stable within equal counts). */
    public List<Reward> rewards() {
        List<Reward> all = new java.util.ArrayList<>(); // drop-only relics have a kill count no one reaches
        banners.values().forEach(d -> all.add(new Reward(RewardKey.banner(d.id()), d.name(), d.family(), d.kills())));
        heads.values().forEach(d -> all.add(new Reward(new RewardKey(RewardKey.HEAD, d.id()), d.name(), d.family(), d.kills())));
        relics.values().forEach(d -> all.add(new Reward(new RewardKey(RewardKey.RELIC, d.id()), d.name(), d.family(), d.kills())));
        all.sort(java.util.Comparator.comparingInt(Reward::kills));
        return all;
    }

    public java.util.Optional<Reward> findReward(RewardKey wanted) {
        return rewards().stream().filter(r -> r.rewardKey().equals(wanted)).findFirst();
    }

    public java.util.Optional<HeadDefinition> findHead(String id) {
        return java.util.Optional.ofNullable(heads.get(id));
    }

    public java.util.Optional<RelicDefinition> findRelic(String id) {
        return java.util.Optional.ofNullable(relics.get(id));
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
        if (mobType.startsWith(BOSS_PREFIX)) {
            String boss = mobType.substring(BOSS_PREFIX.length());
            return families.containsKey(boss) ? boss : null;
        }
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
