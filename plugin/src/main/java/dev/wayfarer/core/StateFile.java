package dev.wayfarer.core;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * One server-wide JSON state file, written off the main thread in the order saves were asked for (one private writer
 * thread), through a temporary file moved into place. Take the JSON on the main thread; hand it here.
 */
public final class StateFile {
    private final Path file;
    private final Logger log;
    private final ExecutorService io;

    public StateFile(Path file, Logger log) {
        this.file = file;
        this.log = log;
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "wayfarer-state-" + file.getFileName());
            t.setDaemon(true);
            return t;
        });
    }

    public Path path() {
        return file;
    }

    /** Queues a write; later saves always land after earlier ones. */
    public void save(String json) {
        io.execute(() -> write(file, json, log));
    }

    /** Writes this last JSON after every queued save and waits for it; for module disable. */
    public void close(String json) {
        save(json);
        io.shutdown();
        try {
            if (!io.awaitTermination(30, TimeUnit.SECONDS)) {
                log.severe("State writes to " + file + " did not finish in 30s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void write(Path file, String json, Logger log) {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                w.write(json);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not save " + file, e);
        }
    }
}
