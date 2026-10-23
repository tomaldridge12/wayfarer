package dev.wayfarer.banners;

import dev.wayfarer.core.PlayerStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data version 2: {"kills": {family: count}, "earned": ["banner:zombie", ...]}. Missing keys default to empty.
 * Version 1 stored bare banner ids in "earned"; they are read as banner rewards.
 */
public final class PlayerDataCodec implements PlayerStore.Codec<PlayerData> {
    public static final int VERSION = 2;

    @Override
    public PlayerData empty() {
        return new PlayerData();
    }

    @Override
    public PlayerData decode(int version, Map<String, Object> raw) {
        PlayerData data = new PlayerData();
        if (raw.get("kills") instanceof Map<?, ?> kills) {
            kills.forEach((family, count) -> {
                if (count instanceof Number n) {
                    data.setKills(String.valueOf(family), n.intValue());
                }
            });
        }
        if (raw.get("earned") instanceof List<?> earned) {
            earned.forEach(id -> data.markEarned(version < 2
                    ? RewardKey.banner(String.valueOf(id)) : RewardKey.parse(String.valueOf(id), RewardKey.BANNER)));
        }
        return data;
    }

    @Override
    public Map<String, Object> encode(PlayerData value) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kills", new LinkedHashMap<>(value.killsView()));
        out.put("earned", List.copyOf(value.earnedView()));
        return out;
    }
}
