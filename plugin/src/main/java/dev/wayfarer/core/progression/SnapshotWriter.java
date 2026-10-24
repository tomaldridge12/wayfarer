package dev.wayfarer.core.progression;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Appends stage snapshot lines to one CSV file on its own thread, writing the header into a new file. */
final class SnapshotWriter {
    private final Path file;
    private final Logger log;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wayfarer-snapshot-io");
        t.setDaemon(true);
        return t;
    });

    SnapshotWriter(Path file, Logger log) {
        this.file = file;
        this.log = log;
    }

    void append(String line) {
        io.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                String text = (Files.exists(file) ? "" : StageSnapshots.HEADER + "\n") + line + "\n";
                Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.log(Level.WARNING, "Could not write stage snapshot to " + file, e);
            }
        });
    }

    /** Finishes queued writes; for plugin disable. */
    void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warning("Stage snapshot writes did not finish in 10s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
