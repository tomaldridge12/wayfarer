package dev.wayfarer.core.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.wayfarer.core.PlayerStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgressionCodecTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path dir;

    private PlayerStore<ProgressionData> store() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return new PlayerStore<>(dir, ProgressionCodec.VERSION, new ProgressionCodec(), log, id -> false);
    }

    @Test
    void everyPartOfTheRecordSurvivesSaveAndReload() {
        PlayerStore<ProgressionData> first = store();
        ProgressionData data = first.load(ID).join();
        data.set("kill:husk", 42);
        data.set("mine:sand", 3_000_000_000L);
        data.markEarned("banner:zombie", 1_800_000_000_000L);
        data.setLastClaim("banner:zombie", 1_800_000_100_000L);
        data.setPity("wither_skeleton_skull", 17);
        first.markDirty(ID);
        first.close();

        ProgressionData again = store().load(ID).join();
        assertEquals(Map.of("kill:husk", 42L, "mine:sand", 3_000_000_000L), again.countersView());
        assertEquals(Map.of("banner:zombie", 1_800_000_000_000L), again.earnedView());
        assertEquals(1_800_000_100_000L, again.lastClaim("banner:zombie"));
        assertEquals(17, again.pity("wither_skeleton_skull"));
    }

    @Test
    void theFileStartsAtVersionOne() throws IOException {
        PlayerStore<ProgressionData> s = store();
        s.load(ID).join().set("kill:husk", 1);
        s.markDirty(ID);
        s.close();
        assertEquals(true, Files.readString(dir.resolve(ID + ".json")).startsWith("{\"version\":1,"));
        assertEquals(1, ProgressionCodec.VERSION);
    }

    @Test
    void missingKeysDefaultToEmpty() throws IOException {
        Files.writeString(dir.resolve(ID + ".json"), "{\"version\":1}");
        ProgressionData data = store().load(ID).join();
        assertEquals(Map.of(), data.countersView());
        assertEquals(Map.of(), data.earnedView());
        assertEquals(0, data.pity("anything"));
    }

    @Test
    void aNewerVersionIsRefused() throws IOException {
        Files.writeString(dir.resolve(ID + ".json"), "{\"version\":2}");
        assertThrows(CompletionException.class, () -> store().load(ID).join());
    }
}
