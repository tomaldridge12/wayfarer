package dev.wayfarer.objectives;

import dev.wayfarer.core.progression.Selectors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns objectives.yml into cosmetics and feats (later tickets add objectives, tables, events and the shop). A bad
 * entry is skipped with a problem; a reward another feat already pays is dropped (rewards are earned once).
 */
public final class ObjectiveConfigParser {
    private final ObjectiveVocabulary names;
    private final List<String> problems = new ArrayList<>();

    private ObjectiveConfigParser(ObjectiveVocabulary names) {
        this.names = names;
    }

    public record Result(Map<String, Cosmetic> cosmetics, Map<String, Feat> feats, List<String> problems) {
    }

    public static Result parse(Map<String, Object> root, ObjectiveVocabulary names) {
        ObjectiveConfigParser p = new ObjectiveConfigParser(names);
        Map<String, Cosmetic> cosmetics = p.cosmetics(root.get("cosmetics"));
        Map<String, Feat> feats = p.feats(root.get("feats"), cosmetics);
        return new Result(cosmetics, feats, List.copyOf(p.problems));
    }

    private Map<String, Cosmetic> cosmetics(Object section) {
        Map<String, Cosmetic> out = new LinkedHashMap<>();
        asMap(section).forEach((id, v) -> {
            Map<String, Object> m = asMap(v);
            String kind = String.valueOf(m.get("kind")).toLowerCase(Locale.ROOT);
            String colour = String.valueOf(m.getOrDefault("colour", "#E8D9A8"));
            String particle = m.get("particle") == null ? null : String.valueOf(m.get("particle")).toLowerCase(Locale.ROOT);
            String name = String.valueOf(m.getOrDefault("name", id));
            if (!Cosmetic.KINDS.contains(kind)) {
                problems.add("Cosmetic " + id + " skipped: kind must be title, trail, hat or pet");
            } else if (!colour.matches("#[0-9a-fA-F]{6}")) {
                problems.add("Cosmetic " + id + " skipped: colour must be #RRGGBB");
            } else if (kind.equals(Cosmetic.TRAIL) && (particle == null || !names.isParticle(particle))) {
                problems.add("Cosmetic " + id + " skipped: a trail needs a particle this server has");
            } else if (kind.equals(Cosmetic.HAT)) {
                hat(id, m).ifPresent(hat -> out.put(id, new Cosmetic(id, kind, name, Integer.parseInt(colour.substring(1), 16), null, hat, null)));
            } else if (kind.equals(Cosmetic.PET)) {
                pet(id, m).ifPresent(pet -> out.put(id, new Cosmetic(id, kind, name, Integer.parseInt(colour.substring(1), 16), null, null, pet)));
            } else {
                out.put(id, new Cosmetic(id, kind, name, Integer.parseInt(colour.substring(1), 16), particle));
            }
        });
        return out;
    }

    /** A hat needs {@code item}: a vanilla item or a pack model key; {@code ability} is optional (checked by its own ticket). */
    private java.util.Optional<Cosmetic.Hat> hat(String id, Map<String, Object> m) {
        String item = m.get("item") == null ? "" : String.valueOf(m.get("item")).toLowerCase(Locale.ROOT);
        boolean model = item.contains(":") && !item.startsWith("minecraft:");
        if (item.isEmpty() || !model && !names.isItem(item.replaceFirst("^minecraft:", ""))) {
            problems.add("Cosmetic " + id + " skipped: a hat needs item: a vanilla item or a pack model like wayfarer:hat/name");
            return java.util.Optional.empty();
        }
        String ability = m.get("ability") == null ? null : String.valueOf(m.get("ability"));
        if (ability != null && !names.isHatAbility(ability)) {
            problems.add("Cosmetic " + id + " skipped: ability '" + ability + "' is not under hat-abilities in curios.yml");
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Cosmetic.Hat(item.replaceFirst("^minecraft:", ""), ability));
    }

    /** A pet needs {@code entity} (a living mob) or {@code model} (a pack model key), not both. */
    private java.util.Optional<Cosmetic.Pet> pet(String id, Map<String, Object> m) {
        String entity = m.get("entity") == null ? null : String.valueOf(m.get("entity")).toLowerCase(Locale.ROOT).replaceFirst("^minecraft:", "");
        String model = m.get("model") == null ? null : String.valueOf(m.get("model")).toLowerCase(Locale.ROOT);
        if (entity == null == (model == null)) {
            problems.add("Cosmetic " + id + " skipped: a pet needs exactly one of entity: or model:");
            return java.util.Optional.empty();
        }
        if (entity != null && !names.isMob(entity)) {
            problems.add("Cosmetic " + id + " skipped: pet entity '" + entity + "' is not a living mob this server has");
            return java.util.Optional.empty();
        }
        if (model != null && !model.contains(":")) {
            problems.add("Cosmetic " + id + " skipped: pet model must be a pack model key like wayfarer:pet/name");
            return java.util.Optional.empty();
        }
        double scale = 1.0;
        if (m.get("scale") != null) {
            if (m.get("scale") instanceof Number n && n.doubleValue() > 0 && n.doubleValue() <= 4) {
                scale = n.doubleValue();
            } else {
                problems.add("Cosmetic " + id + ": scale must be above 0 and at most 4; using 1");
            }
        }
        return java.util.Optional.of(new Cosmetic.Pet(entity, model, !Boolean.FALSE.equals(m.get("baby")),
                m.get("pet-name") == null ? null : String.valueOf(m.get("pet-name")), scale));
    }

    private Map<String, Feat> feats(Object section, Map<String, Cosmetic> cosmetics) {
        Map<String, Feat> out = new LinkedHashMap<>();
        Map<String, String> paidBy = new HashMap<>();
        asMap(section).forEach((id, v) -> {
            Map<String, Object> m = asMap(v);
            String where = "Feat " + id;
            List<Object> raw = new ArrayList<>();
            for (Object o : m.get("counts") instanceof List<?> l ? l : m.get("counts") == null ? List.of() : List.of(m.get("counts"))) {
                String entry = String.valueOf(o).toLowerCase(Locale.ROOT);
                int at = entry.indexOf(":@");
                if (at > 0) {
                    Set<String> group = names.group(entry.substring(at + 2));
                    if (group.isEmpty()) {
                        problems.add(where + ": group " + entry.substring(at + 1) + " is empty or unknown; left out");
                    }
                    group.forEach(g -> raw.add(entry.substring(0, at + 1) + g));
                } else {
                    raw.add(entry);
                }
            }
            Set<String> selector = Selectors.parse(raw, names::tag, problems::add, where);
            boolean distinct = Boolean.TRUE.equals(m.get("distinct"));
            Object at = m.get("at");
            long threshold = "all".equals(String.valueOf(at)) ? selector.stream().filter(s -> !s.endsWith("*")).count()
                    : at instanceof Number n ? n.longValue() : 0;
            List<String> rewards = new ArrayList<>();
            for (Object o : m.get("rewards") instanceof List<?> l ? l : List.of()) {
                String reward = String.valueOf(o).trim();
                int colon = reward.indexOf(':');
                if (colon <= 0 || colon == reward.length() - 1) {
                    problems.add(where + ": reward '" + o + "' is not type:id; dropped");
                } else if (reward.startsWith("cosmetic:") && !cosmetics.containsKey(reward.substring(9))) {
                    problems.add(where + ": reward " + reward + " names no cosmetic in cosmetics; dropped");
                } else if (paidBy.containsKey(reward)) {
                    problems.add(where + ": reward " + reward + " is already paid by feat " + paidBy.get(reward) + "; dropped");
                } else {
                    paidBy.put(reward, id);
                    rewards.add(reward);
                }
            }
            if (!id.matches("[a-z0-9_]+")) {
                problems.add(where + " skipped: ids are lower-case letters, digits and _");
            } else if (selector.isEmpty()) {
                problems.add(where + " skipped: counts lists nothing usable");
            } else if (threshold < 1) {
                problems.add(where + " skipped: at must be a count of 1 or more (or all)");
            } else if ("all".equals(String.valueOf(at)) && !distinct) {
                problems.add(where + " skipped: at: all needs distinct: true");
            } else if (rewards.isEmpty()) {
                problems.add(where + " skipped: it pays nothing");
            } else {
                out.put(id, new Feat(id, String.valueOf(m.getOrDefault("name", id)), String.valueOf(m.getOrDefault("flavour", "")),
                        selector, distinct, threshold, Boolean.TRUE.equals(m.get("hidden")), rewards));
            }
        });
        return out;
    }

    static Map<String, Object> asMap(Object value) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> out.put(String.valueOf(k), v));
        }
        return out;
    }
}
