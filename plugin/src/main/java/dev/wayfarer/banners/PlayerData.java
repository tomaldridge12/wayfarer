package dev.wayfarer.banners;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One player's kill count per mob family and earned rewards. Mutated on the main thread only. */
public final class PlayerData {
    private final Map<String, Integer> kills = new LinkedHashMap<>();
    private final Set<String> earned = new LinkedHashSet<>();
    private final Map<String, Long> lastClaim = new LinkedHashMap<>();
    private final Map<String, Long> earnedAt = new LinkedHashMap<>();

    public int kills(String family) {
        return kills.getOrDefault(family, 0);
    }

    public void setKills(String family, int count) {
        kills.put(family, count);
    }

    public boolean isEarned(RewardKey reward) {
        return earned.contains(reward.toString());
    }

    /** @return true if newly earned */
    public boolean markEarned(RewardKey reward) {
        return earned.add(reward.toString());
    }

    /** When the reward was earned, in epoch milliseconds, or 0 if unknown (earned before dates were kept). */
    public long earnedAt(RewardKey reward) {
        return earnedAt.getOrDefault(reward.toString(), 0L);
    }

    public void setEarnedAt(RewardKey reward, long epochMillis) {
        earnedAt.put(reward.toString(), epochMillis);
    }

    public Map<String, Long> earnedAtView() {
        return Map.copyOf(earnedAt);
    }

    /** When the player last claimed this reward, in epoch milliseconds, or 0 if never. */
    public long lastClaim(RewardKey reward) {
        return lastClaim.getOrDefault(reward.toString(), 0L);
    }

    public void setLastClaim(RewardKey reward, long epochMillis) {
        lastClaim.put(reward.toString(), epochMillis);
    }

    public Map<String, Long> lastClaimView() {
        return Map.copyOf(lastClaim);
    }

    public Map<String, Integer> killsView() {
        return Map.copyOf(kills);
    }

    /** Earned rewards as "type:id" text. */
    public Set<String> earnedView() {
        return Set.copyOf(earned);
    }
}
