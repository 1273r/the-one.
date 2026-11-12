package dev.mox.theone;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public final class ProgressStore implements AutoCloseable {
    private final Connection connection;

    public ProgressStore(File file) throws SQLException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create plugin data directory.");
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_progress (
                        uuid TEXT PRIMARY KEY NOT NULL,
                        kills INTEGER NOT NULL DEFAULT 0 CHECK(kills >= 0)
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS global_state (
                        id INTEGER PRIMARY KEY CHECK(id = 1),
                        holder_uuid TEXT
                    )
                    """);
            statement.executeUpdate("INSERT OR IGNORE INTO global_state (id, holder_uuid) VALUES (1, NULL)");
        }
    }

    public UUID getHolder() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT holder_uuid FROM global_state WHERE id = 1");
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) return null;
            String value = result.getString(1);
            return value == null ? null : UUID.fromString(value);
        }
    }

    public void setHolder(UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE global_state SET holder_uuid = ? WHERE id = 1")) {
            statement.setString(1, uuid == null ? null : uuid.toString());
            statement.executeUpdate();
        }
    }

    public int getKills(UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT kills FROM player_progress WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    public void setKills(UUID uuid, int kills) throws SQLException {
        int safeKills = Math.max(0, kills);
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO player_progress (uuid, kills) VALUES (?, ?)
                ON CONFLICT(uuid) DO UPDATE SET kills = excluded.kills
                """)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, safeKills);
            statement.executeUpdate();
        }
    }

    public int addKill(UUID uuid) throws SQLException {
        int updated = getKills(uuid) + 1;
        setKills(uuid, updated);
        return updated;
    }

    @Override
    public void close() throws SQLException {
        if (!connection.isClosed()) connection.close();
    }
}
