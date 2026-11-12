package dev.mox.theone;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProgressStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void killsAndHolderSurviveDatabaseReopen() throws Exception {
        UUID playerId = UUID.randomUUID();
        Path database = temporaryDirectory.resolve("progress.db");

        try (ProgressStore store = new ProgressStore(database.toFile())) {
            assertEquals(0, store.getKills(playerId));
            store.setKills(playerId, 29);
            assertEquals(30, store.addKill(playerId));
            store.setHolder(playerId);
        }

        try (ProgressStore reopened = new ProgressStore(database.toFile())) {
            assertEquals(30, reopened.getKills(playerId));
            assertEquals(playerId, reopened.getHolder());
        }
    }

    @Test
    void negativeProgressIsClampedAndHolderCanBeCleared() throws Exception {
        UUID playerId = UUID.randomUUID();
        Path database = temporaryDirectory.resolve("clamp.db");

        try (ProgressStore store = new ProgressStore(database.toFile())) {
            store.setKills(playerId, -50);
            store.setHolder(playerId);
            assertEquals(0, store.getKills(playerId));
            store.setHolder(null);
            assertNull(store.getHolder());
        }
    }
}
