package dev.wayfarer.curios;

import dev.wayfarer.core.PlayerStore;
import java.util.LinkedHashMap;
import java.util.Map;

/** What the curios module keeps per player: stored bait. Mutated on the main thread only. */
final class CurioPlayerData {
    static final int VERSION = 1;

    int bait;

    static final class Codec implements PlayerStore.Codec<CurioPlayerData> {
        @Override
        public CurioPlayerData empty() {
            return new CurioPlayerData();
        }

        @Override
        public CurioPlayerData decode(int version, Map<String, Object> raw) {
            CurioPlayerData d = new CurioPlayerData();
            d.bait = raw.get("bait") instanceof Number n ? n.intValue() : 0;
            return d;
        }

        @Override
        public Map<String, Object> encode(CurioPlayerData value) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("bait", value.bait);
            return out;
        }
    }
}
