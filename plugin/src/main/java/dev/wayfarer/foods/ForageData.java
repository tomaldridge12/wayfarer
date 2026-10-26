package dev.wayfarer.foods;

import dev.wayfarer.core.PlayerStore;
import java.util.LinkedHashMap;
import java.util.Map;

/** What foods keeps per player: for each forage ingredient, how many qualifying breaks in a row gave nothing. Main thread only. */
final class ForageData {
    static final int VERSION = 1;

    final Map<String, Integer> streaks = new LinkedHashMap<>();

    int streak(String ingredient) {
        return streaks.getOrDefault(ingredient, 0);
    }

    static final class Codec implements PlayerStore.Codec<ForageData> {
        @Override
        public ForageData empty() {
            return new ForageData();
        }

        @Override
        public ForageData decode(int version, Map<String, Object> raw) {
            ForageData d = new ForageData();
            if (raw.get("streaks") instanceof Map<?, ?> m) {
                m.forEach((k, v) -> {
                    if (v instanceof Number n && n.intValue() > 0) {
                        d.streaks.put(String.valueOf(k), n.intValue());
                    }
                });
            }
            return d;
        }

        @Override
        public Map<String, Object> encode(ForageData value) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("streaks", new LinkedHashMap<>(value.streaks));
            return out;
        }
    }
}
