package dev.wayfarer.objectives;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The server goal and event state, one versioned JSON file: this week's goal, each player's count when first seen this
 * week and their contribution since, whether it was completed and who was paid, every player's last known stage, and
 * the running event. Mutated on the main thread; written off it.
 */
final class ServerGoalState {
    static final int VERSION = 1;
    private static final Gson GSON = new Gson();

    long weekStart;
    String goal;
    String stage;
    final Map<String, Long> baselines = new LinkedHashMap<>();
    final Map<String, Long> contributions = new LinkedHashMap<>();
    boolean completed;
    final Set<String> paid = new LinkedHashSet<>();
    final Map<String, String> playerStages = new LinkedHashMap<>();
    final Map<String, Long> lastSeen = new LinkedHashMap<>();
    long eventStart;
    String event;
    String eventGoal;
    final Map<String, Long> eventBaselines = new LinkedHashMap<>();
    final Map<String, Long> eventContributions = new LinkedHashMap<>();
    boolean eventCompleted;
    final Set<String> eventPaid = new LinkedHashSet<>();
    boolean eventForced;
    /** What a running event changed and must put back (world name -> its random tick speed before). */
    final Map<String, Long> eventRestore = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    static ServerGoalState read(Path file, Logger log) {
        ServerGoalState s = new ServerGoalState();
        if (!Files.exists(file)) {
            return s;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Object> raw = GSON.fromJson(reader, Map.class);
            int version = raw.get("version") instanceof Number n ? n.intValue() : 1;
            if (version > VERSION) {
                throw new IllegalStateException(file + " has version " + version + "; this plugin reads up to " + VERSION);
            }
            s.weekStart = num(raw.get("weekStart"));
            s.goal = str(raw.get("goal"));
            s.stage = str(raw.get("stage"));
            longs(raw.get("baselines"), s.baselines);
            longs(raw.get("contributions"), s.contributions);
            s.completed = Boolean.TRUE.equals(raw.get("completed"));
            strings(raw.get("paid"), s.paid);
            if (raw.get("playerStages") instanceof Map<?, ?> m) {
                m.forEach((k, v) -> s.playerStages.put(String.valueOf(k), String.valueOf(v)));
            }
            longs(raw.get("lastSeen"), s.lastSeen);
            s.eventStart = num(raw.get("eventStart"));
            s.event = str(raw.get("event"));
            s.eventGoal = str(raw.get("eventGoal"));
            longs(raw.get("eventBaselines"), s.eventBaselines);
            longs(raw.get("eventContributions"), s.eventContributions);
            s.eventCompleted = Boolean.TRUE.equals(raw.get("eventCompleted"));
            strings(raw.get("eventPaid"), s.eventPaid);
            s.eventForced = Boolean.TRUE.equals(raw.get("eventForced"));
            longs(raw.get("eventRestore"), s.eventRestore);
        } catch (IOException | RuntimeException e) {
            Path moved = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            log.log(Level.SEVERE, "Unreadable server goal state " + file + ", moved to " + moved.getFileName() + " and starting fresh", e);
            try {
                Files.move(file, moved);
            } catch (IOException ignored) {
                // it stays where it is; the next save overwrites it
            }
            return new ServerGoalState();
        }
        return s;
    }

    String json() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", VERSION);
        out.put("weekStart", weekStart);
        out.put("goal", goal);
        out.put("stage", stage);
        out.put("baselines", baselines);
        out.put("contributions", contributions);
        out.put("completed", completed);
        out.put("paid", paid);
        out.put("playerStages", playerStages);
        out.put("lastSeen", lastSeen);
        out.put("eventStart", eventStart);
        out.put("event", event);
        out.put("eventGoal", eventGoal);
        out.put("eventBaselines", eventBaselines);
        out.put("eventContributions", eventContributions);
        out.put("eventCompleted", eventCompleted);
        out.put("eventPaid", eventPaid);
        out.put("eventForced", eventForced);
        out.put("eventRestore", eventRestore);
        return GSON.toJson(out);
    }

    static void write(Path file, String json, Logger log) {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save the server goal state " + file, e);
        }
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0;
    }

    private static String str(Object o) {
        return o instanceof String s ? s : null;
    }

    private static void longs(Object o, Map<String, Long> into) {
        if (o instanceof Map<?, ?> m) {
            m.forEach((k, v) -> into.put(String.valueOf(k), num(v)));
        }
    }

    private static void strings(Object o, Set<String> into) {
        if (o instanceof Iterable<?> l) {
            l.forEach(x -> into.add(String.valueOf(x)));
        }
    }
}
