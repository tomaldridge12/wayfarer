package dev.wayfarer.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.wayfarer.banners.PlayerData;
import dev.wayfarer.banners.PlayerDataCodec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerStoreTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @TempDir
    Path dir;

    private PlayerStore<PlayerData> store() {
        Logger log = Logger.getAnonymousLogger();
        log.setUseParentHandlers(false);
        return new PlayerStore<>(dir, PlayerDataCodec.VERSION, new PlayerDataCodec(), log, id -> false);
    }

    @Test
    void dataSurvivesSaveAndReloadIncludingOfflinePlayers() {
        PlayerStore<PlayerData> first = store();
        PlayerData data = first.load(ID).join();
        data.setKills("zombie", 42);
        data.markEarned("zombie");
        first.markDirty(ID);
        first.close();

        PlayerData again = store().load(ID).join();
        assertEquals(42, again.kills("zombie"));
        assertTrue(again.isEarned("zombie"));
    }

    @Test
    void fileCarriesAVersionAndMissingKeysDefault() throws IOException {
        Files.writeString(dir.resolve(ID + ".json"), "{\"version\":1}");
        PlayerData data = store().load(ID).join();
        assertEquals(0, data.kills("zombie"));
        assertEquals(java.util.Set.of(), data.earnedView());
    }

    @Test
    void newerDataVersionIsRefusedAndNeverOverwritten() throws IOException {
        Path file = dir.resolve(ID + ".json");
        Files.writeString(file, "{\"version\":99}");
        assertThrows(CompletionException.class, () -> store().load(ID).join());
        assertEquals("{\"version\":99}", Files.readString(file));
    }

    @Test
    void corruptFileIsSetAsideNotDeleted() throws IOException {
        Files.writeString(dir.resolve(ID + ".json"), "{not json");
        PlayerData data = store().load(ID).join();
        assertEquals(0, data.kills("zombie"));
        try (var files = Files.list(dir)) {
            assertTrue(files.anyMatch(p -> p.getFileName().toString().contains(".corrupt-")));
        }
    }
}
