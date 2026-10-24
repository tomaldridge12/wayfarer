package dev.wayfarer.objectives;

import dev.wayfarer.core.PlayerStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the objectives module keeps per player: tokens and their ledger, the current dailies and weekly with the counts
 * they started from, rerolls, the streak, owned and chosen cosmetics, enchants unlocked for the shop, and loot waiting
 * for them to come online. Mutated on the main thread only.
 */
final class ObjectivesPlayerData {
    static final int VERSION = 1;
    static final int LEDGER_KEEP = 100;

    /** One drawn objective: which, the selector's value when its window opened, and whether it is done. */
    static final class Slot {
        String id;
        long baseline;
        boolean done;

        Slot(String id, long baseline, boolean done) {
            this.id = id;
            this.baseline = baseline;
            this.done = done;
        }
    }

    long tokens;
    final List<String> ledger = new ArrayList<>();
    long dailyStart;
    final List<Slot> dailies = new ArrayList<>();
    long rerollDay;
    int rerollsUsed;
    long weeklyStart;
    Slot weekly;
    int streak;
    long lastStreakDay = Long.MIN_VALUE;
    final Set<String> cosmetics = new LinkedHashSet<>();
    String title;
    String trail;
    final Map<String, Integer> unlockedEnchants = new LinkedHashMap<>();
    final List<String> pending = new ArrayList<>();
    long shopRerollDay = Long.MIN_VALUE;
    /** Alloy templates seen in the player's inventory ({@code alloy:template:steel}), for shop copies. */
    final Set<String> ownedTemplates = new LinkedHashSet<>();

    void log(String line) {
        ledger.add(line);
        while (ledger.size() > LEDGER_KEEP) {
            ledger.removeFirst();
        }
    }

    static final class Codec implements PlayerStore.Codec<ObjectivesPlayerData> {
        @Override
        public ObjectivesPlayerData empty() {
            return new ObjectivesPlayerData();
        }

        @Override
        public ObjectivesPlayerData decode(int version, Map<String, Object> raw) {
            ObjectivesPlayerData d = new ObjectivesPlayerData();
            d.tokens = num(raw.get("tokens"));
            strings(raw.get("ledger")).forEach(d.ledger::add);
            d.dailyStart = num(raw.get("dailyStart"));
            if (raw.get("dailies") instanceof List<?> list) {
                list.forEach(o -> slot(o).ifPresent(d.dailies::add));
            }
            d.rerollDay = num(raw.get("rerollDay"));
            d.rerollsUsed = (int) num(raw.get("rerollsUsed"));
            d.weeklyStart = num(raw.get("weeklyStart"));
            d.weekly = slot(raw.get("weekly")).orElse(null);
            d.streak = (int) num(raw.get("streak"));
            d.lastStreakDay = raw.get("lastStreakDay") instanceof Number n ? n.longValue() : Long.MIN_VALUE;
            d.cosmetics.addAll(strings(raw.get("cosmetics")));
            d.title = raw.get("title") instanceof String s ? s : null;
            d.trail = raw.get("trail") instanceof String s ? s : null;
            if (raw.get("unlockedEnchants") instanceof Map<?, ?> m) {
                m.forEach((k, v) -> d.unlockedEnchants.put(String.valueOf(k), (int) num(v)));
            }
            d.pending.addAll(strings(raw.get("pending")));
            d.shopRerollDay = raw.get("shopRerollDay") instanceof Number n ? n.longValue() : Long.MIN_VALUE;
            d.ownedTemplates.addAll(strings(raw.get("ownedTemplates")));
            return d;
        }

        @Override
        public Map<String, Object> encode(ObjectivesPlayerData d) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tokens", d.tokens);
            out.put("ledger", List.copyOf(d.ledger));
            out.put("dailyStart", d.dailyStart);
            out.put("dailies", d.dailies.stream().map(Codec::slot).toList());
            out.put("rerollDay", d.rerollDay);
            out.put("rerollsUsed", d.rerollsUsed);
            out.put("weeklyStart", d.weeklyStart);
            if (d.weekly != null) {
                out.put("weekly", slot(d.weekly));
            }
            out.put("streak", d.streak);
            out.put("lastStreakDay", d.lastStreakDay);
            out.put("cosmetics", List.copyOf(d.cosmetics));
            if (d.title != null) {
                out.put("title", d.title);
            }
            if (d.trail != null) {
                out.put("trail", d.trail);
            }
            out.put("unlockedEnchants", Map.copyOf(d.unlockedEnchants));
            out.put("pending", List.copyOf(d.pending));
            out.put("shopRerollDay", d.shopRerollDay);
            out.put("ownedTemplates", List.copyOf(d.ownedTemplates));
            return out;
        }

        private static Map<String, Object> slot(Slot s) {
            return Map.of("id", s.id, "baseline", s.baseline, "done", s.done);
        }

        private static java.util.Optional<Slot> slot(Object o) {
            if (o instanceof Map<?, ?> m && m.get("id") instanceof String id) {
                return java.util.Optional.of(new Slot(id, num(m.get("baseline")), Boolean.TRUE.equals(m.get("done"))));
            }
            return java.util.Optional.empty();
        }

        private static long num(Object o) {
            return o instanceof Number n ? n.longValue() : 0;
        }

        private static List<String> strings(Object o) {
            List<String> out = new ArrayList<>();
            if (o instanceof List<?> l) {
                l.forEach(x -> out.add(String.valueOf(x)));
            }
            return out;
        }
    }
}
