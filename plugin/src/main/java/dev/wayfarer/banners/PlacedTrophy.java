package dev.wayfarer.banners;

import java.util.UUID;

/**
 * What a placed-trophy record in chunk data names. Banners keep their original bare id ("zombie") so older chunk
 * data still reads; trophy heads are written "head:zombie@<earner uuid>@<earned epoch millis>". No server types.
 */
public record PlacedTrophy(RewardKey reward, UUID earner, long earnedAt) {
    public static PlacedTrophy banner(String bannerId) {
        return new PlacedTrophy(RewardKey.banner(bannerId), null, 0);
    }

    public static PlacedTrophy head(String headId, UUID earner, long earnedAt) {
        return new PlacedTrophy(new RewardKey(RewardKey.HEAD, headId), earner, earnedAt);
    }

    public static PlacedTrophy parse(String text) {
        if (text.indexOf(':') < 0) {
            return banner(text);
        }
        String[] parts = text.split("@");
        UUID earner = null;
        long earnedAt = 0;
        try {
            earner = parts.length > 1 ? UUID.fromString(parts[1]) : null;
            earnedAt = parts.length > 2 ? Long.parseLong(parts[2]) : 0;
        } catch (IllegalArgumentException ignored) {
            // a damaged suffix: keep the reward, lose the earner details
        }
        return new PlacedTrophy(RewardKey.parse(parts[0], RewardKey.BANNER), earner, earnedAt);
    }

    public boolean isBanner() {
        return RewardKey.BANNER.equals(reward.type());
    }

    public boolean isHead() {
        return RewardKey.HEAD.equals(reward.type());
    }

    /** The text stored in chunk data. */
    public String encode() {
        if (isBanner()) {
            return reward.id();
        }
        return reward + (earner == null ? "" : "@" + earner + "@" + earnedAt);
    }
}
