package dev.wayfarer.objectives;

import dev.wayfarer.core.progression.Selectors;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The schedule, stages, daily and weekly pools and reward tables of objectives.yml. A bad pool entry or table line is
 * skipped with a problem; a bad setting keeps its default.
 */
public final class PoolConfigParser {
    static final Set<String> REWARD_KINDS = Set.of("tokens", "supply", "curio", "gear", "cosmetic", "enchant", "item");

    /** Daily, weekly and streak settings. */
    public record Settings(Schedule schedule, List<String> stages, Map<String, String> stageAdvancements, int dailies,
            int freeRerolls, int rerollBase, int rerollStep, int streakEvery, int streakTokens, String streakReward,
            int duplicateTokens) {
    }

    public record Result(Settings settings, Map<String, ObjectiveDefinition> daily, Map<String, ObjectiveDefinition> weekly,
            RewardTables tables, List<String> problems) {
    }

    private final ObjectiveVocabulary names;
    private final Predicate<String> isItem;
    private final List<String> problems = new ArrayList<>();

    private PoolConfigParser(ObjectiveVocabulary names, Predicate<String> isItem) {
        this.names = names;
        this.isItem = isItem;
    }

    public static Result parse(Map<String, Object> root, ObjectiveVocabulary names, Predicate<String> isItem) {
        PoolConfigParser p = new PoolConfigParser(names, isItem);
        Settings settings = p.settings(root);
        RewardTables tables = p.tables(root.get("tables"), settings.duplicateTokens());
        Map<String, ObjectiveDefinition> daily = p.pool("Daily", root.get("daily-pool"), settings, tables, "daily");
        Map<String, ObjectiveDefinition> weekly = p.pool("Weekly", root.get("weekly-pool"), settings, tables, "weekly");
        return new Result(settings, daily, weekly, tables, List.copyOf(p.problems));
    }

    private Settings settings(Map<String, Object> root) {
        Schedule d = Schedule.defaults();
        Map<String, Object> s = ObjectiveConfigParser.asMap(root.get("schedule"));
        ZoneId zone = d.zone();
        try {
            zone = s.get("zone") == null ? zone : ZoneId.of(String.valueOf(s.get("zone")));
        } catch (RuntimeException e) {
            problems.add("schedule.zone '" + s.get("zone") + "' is not a time zone; using " + zone);
        }
        LocalTime daily = time(s.get("daily-reset"), d.dailyReset(), "schedule.daily-reset");
        DayOfWeek weekly = day(s.get("weekly-reset"), d.weeklyDay(), "schedule.weekly-reset");
        String[] start = String.valueOf(s.getOrDefault("event-start", "FRIDAY 18:00")).trim().split("\\s+");
        String[] end = String.valueOf(s.getOrDefault("event-end", "MONDAY 00:00")).trim().split("\\s+");
        DayOfWeek startDay = day(start[0], d.eventStartDay(), "schedule.event-start");
        LocalTime startTime = time(start.length > 1 ? start[1] : null, d.eventStart(), "schedule.event-start");
        DayOfWeek endDay = day(end[0], d.eventEndDay(), "schedule.event-end");
        LocalTime endTime = time(end.length > 1 ? end[1] : null, d.eventEnd(), "schedule.event-end");
        Map<String, String> advancements = new LinkedHashMap<>();
        ObjectiveConfigParser.asMap(root.get("stages")).forEach((stage, adv) -> advancements.put(stage.toLowerCase(Locale.ROOT),
                adv == null ? "" : String.valueOf(adv)));
        if (advancements.isEmpty()) {
            advancements.put("start", "");
        }
        Map<String, Object> dl = ObjectiveConfigParser.asMap(root.get("dailies"));
        Map<String, Object> rr = ObjectiveConfigParser.asMap(dl.get("rerolls"));
        Map<String, Object> st = ObjectiveConfigParser.asMap(dl.get("streak"));
        return new Settings(new Schedule(zone, daily, weekly, startDay, startTime, endDay, endTime), List.copyOf(advancements.keySet()),
                advancements, number(dl.get("count"), 3, 1, 6, "dailies.count"), number(rr.get("free"), 1, 0, 10, "dailies.rerolls.free"),
                number(rr.get("xp-levels"), 3, 0, 100, "dailies.rerolls.xp-levels"), number(rr.get("xp-step"), 2, 0, 100, "dailies.rerolls.xp-step"),
                number(st.get("every"), 7, 0, 365, "dailies.streak.every"), number(st.get("tokens"), 50, 0, 100000, "dailies.streak.tokens"),
                st.get("reward") == null ? null : String.valueOf(st.get("reward")),
                number(root.get("duplicate-tokens"), 30, 0, 100000, "duplicate-tokens"));
    }

    private Map<String, ObjectiveDefinition> pool(String label, Object section, Settings settings, RewardTables tables, String tier) {
        Map<String, ObjectiveDefinition> out = new LinkedHashMap<>();
        ObjectiveConfigParser.asMap(section).forEach((id, v) -> {
            Map<String, Object> m = ObjectiveConfigParser.asMap(v);
            String where = label + " objective " + id;
            Set<String> selector = Selectors.parse(m.get("counts"), names::tag, problems::add, where);
            long target = m.get("target") instanceof Number n ? n.longValue() : 0;
            String stage = String.valueOf(m.getOrDefault("stage", settings.stages().getFirst())).toLowerCase(Locale.ROOT);
            String table = String.valueOf(m.get("table")).toLowerCase(Locale.ROOT);
            int tokens = m.get("tokens") instanceof Number n ? n.intValue() : 0;
            if (selector.isEmpty()) {
                problems.add(where + " skipped: counts lists nothing usable");
            } else if (target < 1) {
                problems.add(where + " skipped: target must be 1 or more");
            } else if (!settings.stages().contains(stage)) {
                problems.add(where + " skipped: stage must be one of " + settings.stages());
            } else if (!tables.has(table, tier)) {
                problems.add(where + " skipped: table " + table + " has no " + tier + " tier");
            } else if (m.get("text") == null) {
                problems.add(where + " skipped: text is missing (what to do, with {n} for the target)");
            } else {
                out.put(id, new ObjectiveDefinition(id, String.valueOf(m.getOrDefault("name", id)), String.valueOf(m.get("text")),
                        selector, Boolean.TRUE.equals(m.get("distinct")), target, stage, table, Math.max(0, tokens)));
            }
        });
        return out;
    }

    private RewardTables tables(Object section, int duplicateTokens) {
        Map<String, Map<String, List<RewardTables.Entry>>> out = new LinkedHashMap<>();
        ObjectiveConfigParser.asMap(section).forEach((table, tiers) -> {
            Map<String, List<RewardTables.Entry>> byTier = new LinkedHashMap<>();
            ObjectiveConfigParser.asMap(tiers).forEach((tier, entries) -> {
                if (!RewardTables.TIERS.contains(tier)) {
                    problems.add("Table " + table + ": tier " + tier + " is not one of " + RewardTables.TIERS + "; skipped");
                    return;
                }
                List<RewardTables.Entry> list = new ArrayList<>();
                for (Object o : entries instanceof List<?> l ? l : List.of()) {
                    RewardTables.Entry e = entry(table + "." + tier, ObjectiveConfigParser.asMap(o));
                    if (e != null) {
                        list.add(e);
                    }
                }
                byTier.put(tier, List.copyOf(list));
            });
            out.put(table.toLowerCase(Locale.ROOT), byTier);
        });
        return new RewardTables(out, duplicateTokens);
    }

    private RewardTables.Entry entry(String where, Map<String, Object> m) {
        String reward = String.valueOf(m.get("reward")).trim();
        String kind = reward.contains(":") ? reward.substring(0, reward.indexOf(':')) : reward;
        String range = String.valueOf(m.getOrDefault(kind.equals("enchant") ? "level" : "amount", "1"));
        int weight = m.get("weight") instanceof Number n ? n.intValue() : 1;
        int min;
        int max;
        try {
            String[] parts = range.split("-");
            min = Integer.parseInt(parts[0].trim());
            max = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : min;
        } catch (NumberFormatException e) {
            problems.add("Table " + where + ": '" + range + "' is not a number or range; line skipped");
            return null;
        }
        if (!REWARD_KINDS.contains(kind) || !kind.equals("tokens") && !reward.contains(":")) {
            problems.add("Table " + where + ": reward '" + reward + "' is not tokens, supply:, curio:, gear:, cosmetic:, enchant: or item:; line skipped");
            return null;
        }
        if (kind.equals("item") && !isItem.test(reward.substring(5))) {
            problems.add("Table " + where + ": " + reward + " is not an item; line skipped");
            return null;
        }
        if (weight < 1 || min < 1 || max < min) {
            problems.add("Table " + where + ": " + reward + " needs a weight of 1 or more and a rising range from 1; line skipped");
            return null;
        }
        return new RewardTables.Entry(reward, weight, min, max);
    }

    private LocalTime time(Object value, LocalTime fallback, String where) {
        if (value == null) {
            return fallback;
        }
        try {
            return LocalTime.parse(String.valueOf(value).length() == 4 ? "0" + value : String.valueOf(value));
        } catch (RuntimeException e) {
            problems.add(where + " time '" + value + "' is not HH:MM; using " + fallback);
            return fallback;
        }
    }

    private DayOfWeek day(Object value, DayOfWeek fallback, String where) {
        if (value == null) {
            return fallback;
        }
        try {
            return DayOfWeek.valueOf(String.valueOf(value).toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            problems.add(where + " day '" + value + "' is not a day of the week; using " + fallback);
            return fallback;
        }
    }

    private int number(Object value, int fallback, int min, int max, String where) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number n && n.intValue() >= min && n.intValue() <= max) {
            return n.intValue();
        }
        problems.add(where + " must be " + min + " to " + max + "; using " + fallback);
        return fallback;
    }
}
