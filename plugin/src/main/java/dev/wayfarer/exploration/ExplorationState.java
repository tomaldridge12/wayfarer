package dev.wayfarer.exploration;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The server's exploration state, one versioned JSON file: whether the dragon has fallen and safe surface is on. */
final class ExplorationState {
    static final int VERSION = 1;
    private static final Gson GSON = new Gson();

    boolean dragonDown;
    boolean safeSurface;
    /** Free Wayfinder pointings each player is owed (from the shop or a curio), kept until used. */
    final Map<String, Integer> charges = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    static ExplorationState read(Path file, Logger log) {
        ExplorationState s = new ExplorationState();
        if (!Files.exists(file)) {
            return s;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Object> raw = GSON.fromJson(reader, Map.class);
            s.dragonDown = Boolean.TRUE.equals(raw.get("dragonDown"));
            s.safeSurface = Boolean.TRUE.equals(raw.get("safeSurface"));
            if (raw.get("charges") instanceof Map<?, ?> m) {
                m.forEach((k, v) -> s.charges.put(String.valueOf(k), v instanceof Number n ? n.intValue() : 0));
            }
        } catch (IOException | RuntimeException e) {
            log.log(Level.SEVERE, "Unreadable exploration state " + file + "; starting with safe surface off", e);
        }
        return s;
    }

    /** The state as JSON; take it on the main thread, write it anywhere. */
    String json() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", VERSION);
        out.put("dragonDown", dragonDown);
        out.put("safeSurface", safeSurface);
        out.put("charges", Map.copyOf(charges));
        return GSON.toJson(out);
    }
}
