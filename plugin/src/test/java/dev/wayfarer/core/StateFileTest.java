package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Server state files: many quick saves land in order, and the last one wins. */
class StateFileTest {
    @TempDir
    Path dir;

    @Test
    void savesLandInTheOrderAskedAndCloseWritesTheLast() throws Exception {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        StateFile file = new StateFile(dir.resolve("state.json"), log);
        for (int i = 0; i < 200; i++) {
            file.save("{\"n\":" + i + "}");
        }
        file.close("{\"n\":\"last\"}");
        assertEquals("{\"n\":\"last\"}", Files.readString(dir.resolve("state.json")));
        assertEquals(false, Files.exists(dir.resolve("state.json.tmp")));
    }
}
