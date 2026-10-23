package dev.wayfarer.core;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Per-player data, one versioned JSON file per player UUID. Files are read and written on a private
 * I/O thread; values are mutated only by the caller's own thread (the main thread) and marked dirty.
 * Free of server types so a module owns its data shape through a {@link Codec}.
 */
public final class PlayerStore<T> {
    /** Converts a module's data to and from the plain map stored as JSON. */
    public interface Codec<T> {
        T empty();

        T decode(int version, Map<String, Object> raw);

        Map<String, Object> encode(T value);
    }

    private static final Gson GSON = new Gson();

    private final Path dir;
    private final int version;
    private final Codec<T> codec;
    private final Logger log;
    private final Predicate<UUID> keep;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wayfarer-store-io");
        t.setDaemon(true);
        return t;
    });
    private final Map<UUID, CompletableFuture<T>> entries = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();

    /** @param keep who stays cached after a flush (online players); others are evicted once saved */
    public PlayerStore(Path dir, int version, Codec<T> codec, Logger log, Predicate<UUID> keep) {
        this.dir = dir;
        this.version = version;
        this.codec = codec;
        this.log = log;
        this.keep = keep;
    }

    /** Loads (or returns the loading) data for a player; completes exceptionally if the file cannot be used. */
    public CompletableFuture<T> load(UUID id) {
        CompletableFuture<T> future = entries.computeIfAbsent(id, u -> CompletableFuture.supplyAsync(() -> read(u), io));
        future.whenComplete((v, e) -> {
            if (e != null) {
                entries.remove(id, future);
            }
        });
        return future;
    }

    /** The data if already loaded, else null. */
    public T cached(UUID id) {
        CompletableFuture<T> future = entries.get(id);
        return future != null && future.isDone() && !future.isCompletedExceptionally() ? future.getNow(null) : null;
    }

    public void markDirty(UUID id) {
        dirty.add(id);
    }

    /** Writes this player's data now if changed. Call on the thread that mutates the data. */
    public void save(UUID id) {
        if (dirty.remove(id)) {
            T value = cached(id);
            if (value != null) {
                String json = GSON.toJson(withVersion(codec.encode(value)));
                io.execute(() -> write(id, json));
            }
        }
    }

    /** Saves everything changed, then drops offline players that are no longer needed. */
    public void flush() {
        for (UUID id : new ArrayList<>(dirty)) {
            save(id);
        }
        entries.keySet().removeIf(id -> !keep.test(id) && !dirty.contains(id));
    }

    /** Saves everything and waits for the writes to finish; for plugin disable. */
    public void close() {
        flush();
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                log.severe("Player data writes did not finish in 30s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Map<String, Object> withVersion(Map<String, Object> encoded) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("version", version);
        out.putAll(encoded);
        return out;
    }

    private Path file(UUID id) {
        return dir.resolve(id + ".json");
    }

    @SuppressWarnings("unchecked")
    private T read(UUID id) {
        Path file = file(id);
        if (!Files.exists(file)) {
            return codec.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, Object> raw = GSON.fromJson(reader, Map.class);
            int fileVersion = ((Number) raw.getOrDefault("version", 1)).intValue();
            if (fileVersion > version) {
                throw new IllegalStateException(file + " has data version " + fileVersion
                        + " but this plugin only reads up to " + version);
            }
            return codec.decode(fileVersion, raw);
        } catch (JsonParseException | ClassCastException | NullPointerException e) {
            Path moved = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
            log.log(Level.SEVERE, "Unreadable player data " + file + ", moved to " + moved.getFileName()
                    + " and starting empty", e);
            try {
                Files.move(file, moved);
            } catch (IOException moveError) {
                throw new IllegalStateException("Could not set aside corrupt " + file, moveError);
            }
            return codec.empty();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file, e);
        }
    }

    private void write(UUID id, String json) {
        Path file = file(id);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(dir);
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save player data " + file, e);
        }
    }
}
