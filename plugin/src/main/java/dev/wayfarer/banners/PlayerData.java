package dev.wayfarer.banners;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One player's kill count per mob family and earned rewards. Mutated on the main thread only. */
public final class PlayerData {
    private final Map<String, Integer> kills = new LinkedHashMap<>();
    private final Set<String> earned = new LinkedHashSet<>();

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

    public Map<String, Integer> killsView() {
        return Map.copyOf(kills);
    }

    /** Earned rewards as "type:id" text. */
    public Set<String> earnedView() {
        return Set.copyOf(earned);
    }
}
