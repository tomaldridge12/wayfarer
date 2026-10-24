package dev.wayfarer.collections;

import dev.wayfarer.core.PlayerStore;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the collections module keeps per player: whether they were given the Journal, their heaviest catch per Almanac
 * species (grams, and when), and their treasure pity (eligible brushes since their last treasure). Main thread.
 */
final class CollectionPlayerData {
    static final int VERSION = 1;

    /** A personal record: the heaviest fish of a species, in grams, and when it was caught (epoch milliseconds). */
    record Catch(long grams, long at) {
    }

    boolean journalGiven;
    final Map<String, Catch> records = new LinkedHashMap<>();
    int treasurePity;

    static final class Codec implements PlayerStore.Codec<CollectionPlayerData> {
        @Override
        public CollectionPlayerData empty() {
            return new CollectionPlayerData();
        }

        @Override
        public CollectionPlayerData decode(int version, Map<String, Object> raw) {
            CollectionPlayerData d = new CollectionPlayerData();
            d.journalGiven = Boolean.TRUE.equals(raw.get("journalGiven"));
            if (raw.get("records") instanceof Map<?, ?> records) {
                records.forEach((k, v) -> {
                    if (v instanceof Map<?, ?> m && m.get("grams") instanceof Number g) {
                        long at = m.get("at") instanceof Number n ? n.longValue() : 0;
                        d.records.put(String.valueOf(k), new Catch(g.longValue(), at));
                    }
                });
            }
            d.treasurePity = raw.get("treasurePity") instanceof Number n ? n.intValue() : 0;
            return d;
        }

        @Override
        public Map<String, Object> encode(CollectionPlayerData value) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("journalGiven", value.journalGiven);
            Map<String, Object> records = new LinkedHashMap<>();
            value.records.forEach((k, c) -> records.put(k, Map.of("grams", c.grams(), "at", c.at())));
            out.put("records", records);
            out.put("treasurePity", value.treasurePity);
            return out;
        }
    }
}
