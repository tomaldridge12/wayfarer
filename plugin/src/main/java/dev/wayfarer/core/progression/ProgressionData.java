package dev.wayfarer.core.progression;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One player's progression record: a counter per activity, earned rewards with when they were earned, claim
 * times and pity counters. Rewards are "type:id" text. Mutated on the main thread only.
 */
public final class ProgressionData {
    private final Map<String, Long> counters = new LinkedHashMap<>();
    private final Map<String, Long> earned = new LinkedHashMap<>();
    private final Map<String, Long> claims = new LinkedHashMap<>();
    private final Map<String, Integer> pity = new LinkedHashMap<>();

    public long count(String activity) {
        return counters.getOrDefault(activity, 0L);
    }

    public void set(String activity, long count) {
        counters.put(activity, count);
    }

    public Map<String, Long> countersView() {
        return Map.copyOf(counters);
    }

    public boolean isEarned(String reward) {
        return earned.containsKey(reward);
    }

    /** @return true if newly earned */
    public boolean markEarned(String reward, long epochMillis) {
        return earned.putIfAbsent(reward, epochMillis) == null;
    }

    /** When the reward was earned, in epoch milliseconds, or 0 if not earned. */
    public long earnedAt(String reward) {
        return earned.getOrDefault(reward, 0L);
    }

    public Map<String, Long> earnedView() {
        return Map.copyOf(earned);
    }

    /** When the player last claimed this reward, in epoch milliseconds, or 0 if never. */
    public long lastClaim(String reward) {
        return claims.getOrDefault(reward, 0L);
    }

    public void setLastClaim(String reward, long epochMillis) {
        claims.put(reward, epochMillis);
    }

    public Map<String, Long> claimsView() {
        return Map.copyOf(claims);
    }

    public int pity(String pityId) {
        return pity.getOrDefault(pityId, 0);
    }

    public void setPity(String pityId, int count) {
        pity.put(pityId, count);
    }

    public Map<String, Integer> pityView() {
        return Map.copyOf(pity);
    }
}
