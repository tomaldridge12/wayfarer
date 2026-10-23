package dev.wayfarer.banners;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** One player's kill count per mob family and earned banner ids. Mutated on the main thread only. */
public final class PlayerData {
    private final Map<String, Integer> kills = new LinkedHashMap<>();
    private final Set<String> earned = new LinkedHashSet<>();

    public int kills(String family) {
        return kills.getOrDefault(family, 0);
    }

    public void setKills(String family, int count) {
        kills.put(family, count);
    }

    public boolean isEarned(String bannerId) {
        return earned.contains(bannerId);
    }

    /** @return true if newly earned */
    public boolean markEarned(String bannerId) {
        return earned.add(bannerId);
    }

    public Map<String, Integer> killsView() {
        return Map.copyOf(kills);
    }

    public Set<String> earnedView() {
        return Set.copyOf(earned);
    }
}
