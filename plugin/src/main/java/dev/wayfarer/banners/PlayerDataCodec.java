package dev.wayfarer.banners;

import dev.wayfarer.core.PlayerStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data version 1: {"kills": {family: count}, "earned": [banner ids]}. Missing keys default to empty. */
public final class PlayerDataCodec implements PlayerStore.Codec<PlayerData> {
    public static final int VERSION = 1;

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
            earned.forEach(id -> data.markEarned(String.valueOf(id)));
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
