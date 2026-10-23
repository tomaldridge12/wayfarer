package dev.wayfarer.banners;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The reward rules: which families a kill counts toward, what is earned, what can be claimed. No server types. */
public final class RewardRules {
    public record KillResult(boolean counted, List<String> families, List<Reward> newlyEarned) {
        static final KillResult NOT_COUNTED = new KillResult(false, List.of(), List.of());
    }

    public enum ClaimResult {
        OK, UNKNOWN_REWARD, NOT_EARNED, COOLDOWN
    }

    /** How long a player waits between claims of the same reward. */
    public static final long CLAIM_COOLDOWN_MILLIS = 24L * 60 * 60 * 1000;

    private RewardRules() {
    }

    /**
     * The families a kill counts toward: the mob's own family, if it has one, and the all-mobs family for
     * hostile mobs (or any mob, if configured). Empty when the kill does not count at all.
     */
    public static List<String> countedFamilies(Kill kill, BannerSet set) {
        CountingSettings s = set.counting();
        if (kill.killer() != Kill.Killer.PLAYER || kill.tamed() || kill.leashed()
                || s.excludedSpawnReasons().contains(kill.spawnReason().toUpperCase(Locale.ROOT))
                || kill.ageTicks() < s.minLifetimeTicks()) {
            return List.of();
        }
        List<String> families = new ArrayList<>(2);
        String own = set.familyOf(kill.mobType());
        if (own != null) {
            families.add(own);
        }
        if (kill.hostile() || !s.allMobsHostileOnly()) {
            families.add(MobFamily.ALL);
        }
        return families;
    }

    /** Counts a kill for the player and returns any rewards that kill newly earned. */
    public static KillResult recordKill(PlayerData data, Kill kill, BannerSet set) {
        List<String> families = countedFamilies(kill, set);
        if (families.isEmpty()) {
            return KillResult.NOT_COUNTED;
        }
        List<Reward> earned = new ArrayList<>();
        for (String family : families) {
            data.setKills(family, data.kills(family) + 1);
            earned.addAll(award(data, set, family));
        }
        return new KillResult(true, families, earned);
    }

    /**
     * A boss defeat for one player who damaged it: +1 for the boss family, its base family when configured, and
     * the all-mobs family. Returns any rewards that newly earned.
     */
    public static KillResult recordBossKill(PlayerData data, BannerSet set, String bossFamily) {
        List<String> families = new ArrayList<>();
        if (set.families().containsKey(bossFamily)) {
            families.add(bossFamily);
        }
        String base = set.baseFamilyOf(bossFamily);
        if (base != null && set.bossLoot().countTowardBase()) {
            families.add(base);
        }
        families.add(MobFamily.ALL);
        List<Reward> earned = new ArrayList<>();
        for (String family : families) {
            data.setKills(family, data.kills(family) + 1);
            earned.addAll(award(data, set, family));
        }
        return new KillResult(true, families, earned);
    }

    /**
     * Sets a kill count (for operators) and returns any rewards that newly earned. {@code target} is a family
     * id (including the all-mobs family) or, for convenience, a banner id meaning that banner's family.
     */
    public static Optional<KillResult> setKills(PlayerData data, BannerSet set, String target, int count) {
        Optional<String> family = set.families().containsKey(target) || MobFamily.ALL.equals(target)
                ? Optional.of(target) : set.find(target).map(BannerDefinition::family);
        return family.map(f -> {
            data.setKills(f, Math.max(0, count));
            return new KillResult(true, List.of(f), award(data, set, f));
        });
    }

    private static List<Reward> award(PlayerData data, BannerSet set, String family) {
        List<Reward> earned = new ArrayList<>();
        for (Reward reward : set.rewards()) {
            if (reward.family().equals(family) && data.kills(family) >= reward.kills()
                    && data.markEarned(reward.rewardKey())) {
                earned.add(reward);
            }
        }
        return earned;
    }

    /** Whether the player may claim a copy now; on OK, records the claim time. */
    public static ClaimResult claim(PlayerData data, BannerSet set, RewardKey wanted, long nowMillis) {
        if (set.findReward(wanted).isEmpty()) {
            return ClaimResult.UNKNOWN_REWARD;
        }
        if (!data.isEarned(wanted)) {
            return ClaimResult.NOT_EARNED;
        }
        if (nowMillis - data.lastClaim(wanted) < CLAIM_COOLDOWN_MILLIS) {
            return ClaimResult.COOLDOWN;
        }
        data.setLastClaim(wanted, nowMillis);
        return ClaimResult.OK;
    }

    /** Milliseconds until the reward can be claimed again; 0 when it can be now. */
    public static long claimWait(PlayerData data, RewardKey wanted, long nowMillis) {
        return Math.max(0, data.lastClaim(wanted) + CLAIM_COOLDOWN_MILLIS - nowMillis);
    }
}
