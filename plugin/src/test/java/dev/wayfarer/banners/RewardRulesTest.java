package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RewardRulesTest {
    private static final long NOW = 1_800_000_000_000L;

    private static final BannerDefinition ZOMBIE_BANNER =
            new BannerDefinition("zombie", "Zombie", "green", List.of(), "zombie", 3, 10, 10, 16);
    private static final BannerDefinition ELITE_BANNER =
            new BannerDefinition("zombie2", "Zombie 2", "green", List.of(), "zombie", 5, 10, 10, 16);
    private static final BannerSet SET = set(CountingSettings.defaults());

    private static BannerSet set(CountingSettings counting) {
        return new BannerSet(
                Map.of("zombie", new MobFamily("zombie", Set.of("zombie", "husk")),
                        "creeper", new MobFamily("creeper", Set.of("creeper"))),
                new java.util.LinkedHashMap<>(Map.of("zombie", ZOMBIE_BANNER)) {{ put("zombie2", ELITE_BANNER); }},
                counting, List.of());
    }

    private static Kill kill(String mob) {
        return new Kill(Kill.Killer.PLAYER, mob, "NATURAL", false, false, 1000);
    }

    @Test
    void playerKillCountsTowardTheMobsFamily() {
        PlayerData data = new PlayerData();
        assertTrue(RewardRules.recordKill(data, kill("husk"), SET).counted());
        assertEquals(1, data.kills("zombie"));
        assertEquals(0, data.kills("creeper"));
    }

    @Test
    void passiveMobsOutsideEveryFamilyDoNotCount() {
        PlayerData data = new PlayerData();
        Kill cow = new Kill(Kill.Killer.PLAYER, "cow", "NATURAL", false, false, 1000, false);
        assertFalse(RewardRules.recordKill(data, cow, SET).counted());
    }

    @Test
    void onlyPlayerCreditCounts() {
        for (Kill.Killer other : List.of(Kill.Killer.MOB_OR_PET, Kill.Killer.ENVIRONMENT)) {
            Kill k = new Kill(other, "zombie", "NATURAL", false, false, 1000);
            assertFalse(RewardRules.recordKill(new PlayerData(), k, SET).counted(), other.name());
        }
    }

    @Test
    void defaultSpawnReasonsAreExcludedButCustomIsNot() {
        for (String reason : List.of("SPAWNER", "TRIAL_SPAWNER", "OMINOUS_ITEM_SPAWNER", "SPAWNER_EGG",
                "SLIME_SPLIT", "BREEDING", "EGG", "DISPENSE_EGG")) {
            Kill k = new Kill(Kill.Killer.PLAYER, "zombie", reason, false, false, 1000);
            assertFalse(RewardRules.recordKill(new PlayerData(), k, SET).counted(), reason);
        }
        Kill custom = new Kill(Kill.Killer.PLAYER, "zombie", "CUSTOM", false, false, 1000);
        assertTrue(RewardRules.recordKill(new PlayerData(), custom, SET).counted());
    }

    @Test
    void exclusionListIsConfigurable() {
        BannerSet relaxed = set(new CountingSettings(Set.of(), 0));
        Kill k = new Kill(Kill.Killer.PLAYER, "zombie", "SPAWNER", false, false, 1000);
        assertTrue(RewardRules.recordKill(new PlayerData(), k, relaxed).counted());
    }

    @Test
    void tamedAndLeashedMobsDoNotCount() {
        assertFalse(RewardRules.recordKill(new PlayerData(),
                new Kill(Kill.Killer.PLAYER, "zombie", "NATURAL", true, false, 1000), SET).counted());
        assertFalse(RewardRules.recordKill(new PlayerData(),
                new Kill(Kill.Killer.PLAYER, "zombie", "NATURAL", false, true, 1000), SET).counted());
    }

    @Test
    void minimumLifetimeExcludesYoungMobsOnlyWhenSet() {
        Kill young = new Kill(Kill.Killer.PLAYER, "zombie", "NATURAL", false, false, 10);
        assertTrue(RewardRules.recordKill(new PlayerData(), young, SET).counted());
        BannerSet strict = set(new CountingSettings(Set.of(), 100));
        assertFalse(RewardRules.recordKill(new PlayerData(), young, strict).counted());
        assertTrue(RewardRules.recordKill(new PlayerData(),
                new Kill(Kill.Killer.PLAYER, "zombie", "NATURAL", false, false, 100), strict).counted());
    }

    @Test
    void bannerIsEarnedExactlyOnceAtItsKillCount() {
        PlayerData data = new PlayerData();
        for (int i = 1; i <= 2; i++) {
            assertEquals(List.of(), RewardRules.recordKill(data, kill("zombie"), SET).newlyEarned());
        }
        assertEquals(List.of(reward(ZOMBIE_BANNER)), RewardRules.recordKill(data, kill("zombie"), SET).newlyEarned());
        assertEquals(List.of(), RewardRules.recordKill(data, kill("zombie"), SET).newlyEarned());
        assertEquals(List.of(reward(ELITE_BANNER)), RewardRules.recordKill(data, kill("zombie"), SET).newlyEarned());
        assertTrue(data.isEarned(RewardKey.banner("zombie")));
    }

    @Test
    void setKillsAwardsCrossedThresholdsOnceAndRejectsUnknownBanners() {
        PlayerData data = new PlayerData();
        var result = RewardRules.setKills(data, SET, "zombie", 99).orElseThrow();
        assertEquals(List.of(reward(ZOMBIE_BANNER), reward(ELITE_BANNER)), result.newlyEarned());
        assertEquals(List.of(), RewardRules.setKills(data, SET, "zombie", 200).orElseThrow().newlyEarned());
        assertEquals(Optional.empty(), RewardRules.setKills(data, SET, "nope", 1));
    }

    @Test
    void claimNeedsAnEarnedRewardStillInConfig() {
        PlayerData data = new PlayerData();
        assertEquals(RewardRules.ClaimResult.NOT_EARNED, RewardRules.claim(data, SET, RewardKey.banner("zombie"), NOW));
        assertEquals(RewardRules.ClaimResult.UNKNOWN_REWARD, RewardRules.claim(data, SET, RewardKey.banner("nope"), NOW));
        data.markEarned(RewardKey.banner("zombie"));
        assertEquals(RewardRules.ClaimResult.OK, RewardRules.claim(data, SET, RewardKey.banner("zombie"), NOW));
        data.markEarned(RewardKey.banner("gone"));
        assertEquals(RewardRules.ClaimResult.UNKNOWN_REWARD, RewardRules.claim(data, SET, RewardKey.banner("gone"), NOW));
    }

    private static Reward reward(BannerDefinition def) {
        return new Reward(RewardKey.banner(def.id()), def.name(), def.family(), def.kills());
    }

    @Test
    void hostileKillsAlsoCountTowardAllMobs() {
        PlayerData data = new PlayerData();
        assertEquals(List.of("zombie", MobFamily.ALL), RewardRules.recordKill(data, kill("zombie"), SET).families());
        // A hostile mob outside every family still counts toward all mobs.
        assertEquals(List.of(MobFamily.ALL), RewardRules.recordKill(data, kill("witch"), SET).families());
        assertEquals(2, data.kills(MobFamily.ALL));
        assertEquals(1, data.kills("zombie"));
    }

    @Test
    void passiveMobsCountTowardAllMobsOnlyWhenConfigured() {
        Kill cow = new Kill(Kill.Killer.PLAYER, "cow", "NATURAL", false, false, 1000, false);
        assertFalse(RewardRules.recordKill(new PlayerData(), cow, SET).counted());
        BannerSet any = set(new CountingSettings(CountingSettings.defaults().excludedSpawnReasons(), 0, false));
        PlayerData data = new PlayerData();
        assertEquals(List.of(MobFamily.ALL), RewardRules.recordKill(data, cow, any).families());
        assertEquals(1, data.kills(MobFamily.ALL));
    }

    @Test
    void setKillsAcceptsAFamilyIdIncludingAllMobs() {
        PlayerData data = new PlayerData();
        assertEquals(List.of("zombie"), RewardRules.setKills(data, SET, "zombie", 5).orElseThrow().families());
        assertEquals(List.of(MobFamily.ALL), RewardRules.setKills(data, SET, MobFamily.ALL, 500).orElseThrow().families());
        assertEquals(500, data.kills(MobFamily.ALL));
    }

    @Test
    void rewardKeysParseWithABannerDefault() {
        assertEquals(RewardKey.banner("zombie"), RewardKey.parse("zombie", RewardKey.BANNER));
        assertEquals(new RewardKey("head", "zombie"), RewardKey.parse("HEAD:zombie", RewardKey.BANNER));
        assertEquals("banner:zombie", RewardKey.banner("zombie").toString());
    }

    @Test
    void claimIsLimitedToOncePerDayPerReward() {
        PlayerData data = new PlayerData();
        RewardKey zombie = RewardKey.banner("zombie");
        data.markEarned(zombie);
        assertEquals(RewardRules.ClaimResult.OK, RewardRules.claim(data, SET, zombie, NOW));
        assertEquals(RewardRules.ClaimResult.COOLDOWN, RewardRules.claim(data, SET, zombie, NOW + 1000));
        assertEquals(RewardRules.CLAIM_COOLDOWN_MILLIS - 1000, RewardRules.claimWait(data, zombie, NOW + 1000));
        assertEquals(RewardRules.ClaimResult.OK, RewardRules.claim(data, SET, zombie, NOW + RewardRules.CLAIM_COOLDOWN_MILLIS));
    }

    @Test
    void headsAndRelicsAreEarnedAlongTheLadder() {
        BannerSet ladder = new BannerSet(SET.families(), SET.banners(),
                Map.of("zombie", new HeadDefinition("zombie", "Zombie Trophy", "zombie", 4)),
                Map.of("zombie_weapon", new RelicDefinition("zombie_weapon", "Zombie Cleaver", "zombie", 6, 1,
                                RelicDefinition.Slot.WEAPON),
                        "wayfarers_blade", new RelicDefinition("wayfarers_blade", "Wayfarer's Blade", MobFamily.ALL, 7, 2,
                                RelicDefinition.Slot.WEAPON)),
                CountingSettings.defaults(), Map.of(), List.of());
        PlayerData data = new PlayerData();
        List<String> earned = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            RewardRules.recordKill(data, kill("zombie"), ladder).newlyEarned()
                    .forEach(r -> earned.add(r.rewardKey().toString()));
        }
        assertEquals(List.of("banner:zombie", "head:zombie", "banner:zombie2", "relic:zombie_weapon",
                "relic:wayfarers_blade"), earned);
        assertTrue(ladder.findReward(new RewardKey("relic", "zombie_weapon")).isPresent());
    }
}
