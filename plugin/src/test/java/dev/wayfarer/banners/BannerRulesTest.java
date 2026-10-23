package dev.wayfarer.banners;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BannerRulesTest {
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
        assertTrue(BannerRules.recordKill(data, kill("husk"), SET).counted());
        assertEquals(1, data.kills("zombie"));
        assertEquals(0, data.kills("creeper"));
    }

    @Test
    void mobsOutsideEveryFamilyDoNotCount() {
        PlayerData data = new PlayerData();
        assertFalse(BannerRules.recordKill(data, kill("cow"), SET).counted());
    }

    @Test
    void onlyPlayerCreditCounts() {
        for (Kill.Killer other : List.of(Kill.Killer.MOB_OR_PET, Kill.Killer.ENVIRONMENT)) {
            Kill k = new Kill(other, "zombie", "NATURAL", false, false, 1000);
            assertFalse(BannerRules.recordKill(new PlayerData(), k, SET).counted(), other.name());
        }
    }

    @Test
    void defaultSpawnReasonsAreExcludedButCustomIsNot() {
        for (String reason : List.of("SPAWNER", "TRIAL_SPAWNER", "OMINOUS_ITEM_SPAWNER", "SPAWNER_EGG",
                "SLIME_SPLIT", "BREEDING", "EGG", "DISPENSE_EGG")) {
            Kill k = new Kill(Kill.Killer.PLAYER, "zombie", reason, false, false, 1000);
            assertFalse(BannerRules.recordKill(new PlayerData(), k, SET).counted(), reason);
        }
        Kill custom = new Kill(Kill.Killer.PLAYER, "zombie", "CUSTOM", false, false, 1000);
        assertTrue(BannerRules.recordKill(new PlayerData(), custom, SET).counted());
    }

    @Test
    void exclusionListIsConfigurable() {
        BannerSet relaxed = set(new CountingSettings(Set.of(), 0));
        Kill k = new Kill(Kill.Killer.PLAYER, "zombie", "SPAWNER", false, false, 1000);
        assertTrue(BannerRules.recordKill(new PlayerData(), k, relaxed).counted());
    }

    @Test
    void tamedAndLeashedMobsDoNotCount() {
        assertFalse(BannerRules.recordKill(new PlayerData(),
                new Kill(Kill.Killer.PLAYER, "zombie", "NATURAL", true, false, 1000), SET).counted());
        assertFalse(BannerRules.recordKill(new PlayerData(),
                new Kill(Kill.Killer.PLAYER, "zombie", "NATURAL", false, true, 1000), SET).counted());
    }

    @Test
    void minimumLifetimeExcludesYoungMobsOnlyWhenSet() {
        Kill young = new Kill(Kill.Killer.PLAYER, "zombie", "NATURAL", false, false, 10);
        assertTrue(BannerRules.recordKill(new PlayerData(), young, SET).counted());
        BannerSet strict = set(new CountingSettings(Set.of(), 100));
        assertFalse(BannerRules.recordKill(new PlayerData(), young, strict).counted());
        assertTrue(BannerRules.recordKill(new PlayerData(),
                new Kill(Kill.Killer.PLAYER, "zombie", "NATURAL", false, false, 100), strict).counted());
    }

    @Test
    void bannerIsEarnedExactlyOnceAtItsKillCount() {
        PlayerData data = new PlayerData();
        for (int i = 1; i <= 2; i++) {
            assertEquals(List.of(), BannerRules.recordKill(data, kill("zombie"), SET).newlyEarned());
        }
        assertEquals(List.of(ZOMBIE_BANNER), BannerRules.recordKill(data, kill("zombie"), SET).newlyEarned());
        assertEquals(List.of(), BannerRules.recordKill(data, kill("zombie"), SET).newlyEarned());
        assertEquals(List.of(ELITE_BANNER), BannerRules.recordKill(data, kill("zombie"), SET).newlyEarned());
        assertTrue(data.isEarned("zombie"));
    }

    @Test
    void setKillsAwardsCrossedThresholdsOnceAndRejectsUnknownBanners() {
        PlayerData data = new PlayerData();
        var result = BannerRules.setKills(data, SET, "zombie", 99).orElseThrow();
        assertEquals(List.of(ZOMBIE_BANNER, ELITE_BANNER), result.newlyEarned());
        assertEquals(List.of(), BannerRules.setKills(data, SET, "zombie", 200).orElseThrow().newlyEarned());
        assertEquals(Optional.empty(), BannerRules.setKills(data, SET, "nope", 1));
    }

    @Test
    void claimNeedsAnEarnedBannerStillInConfig() {
        PlayerData data = new PlayerData();
        assertEquals(BannerRules.ClaimResult.NOT_EARNED, BannerRules.claim(data, SET, "zombie"));
        assertEquals(BannerRules.ClaimResult.UNKNOWN_BANNER, BannerRules.claim(data, SET, "nope"));
        data.markEarned("zombie");
        assertEquals(BannerRules.ClaimResult.OK, BannerRules.claim(data, SET, "zombie"));
        data.markEarned("gone");
        assertEquals(BannerRules.ClaimResult.UNKNOWN_BANNER, BannerRules.claim(data, SET, "gone"));
    }
}
