package dev.wayfarer.banners;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** The banner rules: what counts, what is earned, what can be claimed. No server types. */
public final class BannerRules {
    public record KillResult(boolean counted, String family, List<BannerDefinition> newlyEarned) {
        static final KillResult NOT_COUNTED = new KillResult(false, null, List.of());
    }

    public enum ClaimResult {
        OK, UNKNOWN_BANNER, NOT_EARNED
    }

    private BannerRules() {
    }

    /** Whether a kill counts toward a mob family at all, and which one. */
    public static Optional<String> countedFamily(Kill kill, BannerSet set) {
        CountingSettings s = set.counting();
        if (kill.killer() != Kill.Killer.PLAYER || kill.tamed() || kill.leashed()
                || s.excludedSpawnReasons().contains(kill.spawnReason().toUpperCase(Locale.ROOT))
                || kill.ageTicks() < s.minLifetimeTicks()) {
            return Optional.empty();
        }
        return Optional.ofNullable(set.familyOf(kill.mobType()));
    }

    /** Counts a kill for the player and returns any banners that kill newly earned. */
    public static KillResult recordKill(PlayerData data, Kill kill, BannerSet set) {
        Optional<String> family = countedFamily(kill, set);
        if (family.isEmpty()) {
            return KillResult.NOT_COUNTED;
        }
        data.setKills(family.get(), data.kills(family.get()) + 1);
        return new KillResult(true, family.get(), award(data, set, family.get()));
    }

    /** Sets a family's kill count (for operators) and returns any banners that newly earned. */
    public static Optional<KillResult> setKills(PlayerData data, BannerSet set, String bannerId, int count) {
        return set.find(bannerId).map(def -> {
            data.setKills(def.family(), Math.max(0, count));
            return new KillResult(true, def.family(), award(data, set, def.family()));
        });
    }

    private static List<BannerDefinition> award(PlayerData data, BannerSet set, String family) {
        List<BannerDefinition> earned = new ArrayList<>();
        for (BannerDefinition def : set.banners().values()) {
            if (def.family().equals(family) && data.kills(family) >= def.kills() && data.markEarned(def.id())) {
                earned.add(def);
            }
        }
        return earned;
    }

    public static ClaimResult claim(PlayerData data, BannerSet set, String bannerId) {
        if (set.find(bannerId).isEmpty()) {
            return ClaimResult.UNKNOWN_BANNER;
        }
        return data.isEarned(bannerId) ? ClaimResult.OK : ClaimResult.NOT_EARNED;
    }
}
