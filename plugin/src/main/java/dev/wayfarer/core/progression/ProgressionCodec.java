package dev.wayfarer.core.progression;

import dev.wayfarer.core.PlayerStore;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Data version 1: {"counters": {"kill:husk": n}, "earned": {"banner:zombie": epochMillis},
 * "claims": {"banner:zombie": epochMillis}, "pity": {"wither_skeleton_skull": n}}. Missing keys default to empty.
 */
public final class ProgressionCodec implements PlayerStore.Codec<ProgressionData> {
    public static final int VERSION = 1;

    @Override
    public ProgressionData empty() {
        return new ProgressionData();
    }

    @Override
    public ProgressionData decode(int version, Map<String, Object> raw) {
        ProgressionData data = new ProgressionData();
        numbers(raw.get("counters"), (k, n) -> data.set(k, n.longValue()));
        numbers(raw.get("earned"), (k, n) -> data.markEarned(k, n.longValue()));
        numbers(raw.get("claims"), (k, n) -> data.setLastClaim(k, n.longValue()));
        numbers(raw.get("pity"), (k, n) -> data.setPity(k, n.intValue()));
        return data;
    }

    private static void numbers(Object section, BiConsumer<String, Number> into) {
        if (section instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (v instanceof Number n) {
                    into.accept(String.valueOf(k), n);
                }
            });
        }
    }

    @Override
    public Map<String, Object> encode(ProgressionData value) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("counters", new java.util.TreeMap<>(value.countersView()));
        out.put("earned", new java.util.TreeMap<>(value.earnedView()));
        out.put("claims", new java.util.TreeMap<>(value.claimsView()));
        out.put("pity", new java.util.TreeMap<>(value.pityView()));
        return out;
    }
}
