package com.okimc.edtoolsperks.database;

import com.okimc.edtoolsperks.model.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Repository for player data CRUD operations.
 * Handles the edtoolsperks_players table.
 *
 * <p>All methods return CompletableFuture for async execution.</p>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class PlayerRepository {

    private final DatabaseManager dbManager;
    private final String tableName;

    /**
     * Creates a new PlayerRepository instance.
     *
     * @param dbManager The database manager
     */
    public PlayerRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.tableName = dbManager.getPlayersTable();
    }

    /**
     * Loads player data from the database.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture with Optional PlayerData (empty if not found)
     */
    public CompletableFuture<Optional<PlayerData>> load(UUID uuid) {
        return dbManager.executeAsync(conn -> {
            String sql = "SELECT rolls, pity_count, animations_enabled FROM " + tableName +
                " WHERE player_uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        int rolls = rs.getInt("rolls");
                        int pityCount = rs.getInt("pity_count");
                        boolean animationsEnabled = rs.getBoolean("animations_enabled");

                        PlayerData data = new PlayerData(uuid, rolls, pityCount, animationsEnabled);
                        data.markClean();

                        return Optional.of(data);
                    }
                }
            }

            return Optional.empty();
        });
    }

    /**
     * Saves player data to the database (insert or update).
     *
     * @param data The player data to save
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> save(PlayerData data) {
        return dbManager.executeAsyncVoid(conn -> {
            String sql;
            if (dbManager.isUsingSqlite()) {
                sql = "INSERT OR REPLACE INTO " + tableName +
                    " (player_uuid, rolls, pity_count, animations_enabled) VALUES (?, ?, ?, ?)";
            } else {
                sql = "INSERT INTO " + tableName +
                    " (player_uuid, rolls, pity_count, animations_enabled) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE rolls = VALUES(rolls), pity_count = VALUES(pity_count), " +
                    "animations_enabled = VALUES(animations_enabled)";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, data.getUuidString());
                ps.setInt(2, data.getRolls());
                ps.setInt(3, data.getPityCount());
                ps.setBoolean(4, data.isAnimationsEnabled());

                ps.executeUpdate();
                data.markClean();
            }
        });
    }

    /**
     * Creates a new player entry with default values.
     *
     * @param uuid The player's UUID
     * @param defaultAnimationsEnabled Default animation setting from config
     * @return CompletableFuture with the created PlayerData
     */
    public CompletableFuture<PlayerData> create(UUID uuid, boolean defaultAnimationsEnabled) {
        return dbManager.executeAsync(conn -> {
            String sql;
            if (dbManager.isUsingSqlite()) {
                sql = "INSERT OR IGNORE INTO " + tableName +
                    " (player_uuid, rolls, pity_count, animations_enabled) VALUES (?, 0, 0, ?)";
            } else {
                sql = "INSERT IGNORE INTO " + tableName +
                    " (player_uuid, rolls, pity_count, animations_enabled) VALUES (?, 0, 0, ?)";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setBoolean(2, defaultAnimationsEnabled);
                ps.executeUpdate();
            }

            PlayerData data = new PlayerData(uuid, 0, 0, defaultAnimationsEnabled);
            data.markClean();
            return data;
        });
    }

    /**
     * Loads player data or creates if not exists.
     *
     * @param uuid The player's UUID
     * @param defaultAnimationsEnabled Default animation setting for new players
     * @return CompletableFuture with the PlayerData
     */
    public CompletableFuture<PlayerData> loadOrCreate(UUID uuid, boolean defaultAnimationsEnabled) {
        return load(uuid).thenCompose(optionalData -> {
            if (optionalData.isPresent()) {
                return CompletableFuture.completedFuture(optionalData.get());
            } else {
                return create(uuid, defaultAnimationsEnabled);
            }
        });
    }

    /**
     * Updates only the rolls count.
     *
     * @param uuid  The player's UUID
     * @param rolls The new rolls count
     * @return CompletableFuture that completes when updated
     */
    public CompletableFuture<Void> updateRolls(UUID uuid, int rolls) {
        return dbManager.executeAsyncVoid(conn -> {
            String sql = "UPDATE " + tableName + " SET rolls = ? WHERE player_uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, rolls);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    /**
     * Sets the rolls count for a player.
     * Alias for updateRolls for command consistency.
     *
     * @param uuid  The player's UUID
     * @param rolls The new rolls count
     * @return CompletableFuture that completes when updated
     */
    public CompletableFuture<Void> setRolls(UUID uuid, int rolls) {
        return updateRolls(uuid, Math.max(0, rolls));
    }

    /**
     * Adds rolls to a player's count.
     *
     * @param uuid   The player's UUID
     * @param amount The amount to add
     * @return CompletableFuture that completes when updated
     */
    public CompletableFuture<Void> addRolls(UUID uuid, int amount) {
        return dbManager.executeAsyncVoid(conn -> {
            String sql = "UPDATE " + tableName + " SET rolls = rolls + ? WHERE player_uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, amount);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    /**
     * Updates only the pity count.
     *
     * @param uuid      The player's UUID
     * @param pityCount The new pity count
     * @return CompletableFuture that completes when updated
     */
    public CompletableFuture<Void> updatePityCount(UUID uuid, int pityCount) {
        return dbManager.executeAsyncVoid(conn -> {
            String sql = "UPDATE " + tableName + " SET pity_count = ? WHERE player_uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, pityCount);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    /**
     * Resets the pity count to zero.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture that completes when updated
     */
    public CompletableFuture<Void> resetPityCount(UUID uuid) {
        return updatePityCount(uuid, 0);
    }

    /**
     * Updates only the animations enabled setting.
     *
     * @param uuid    The player's UUID
     * @param enabled Whether animations are enabled
     * @return CompletableFuture that completes when updated
     */
    public CompletableFuture<Void> updateAnimationsEnabled(UUID uuid, boolean enabled) {
        return dbManager.executeAsyncVoid(conn -> {
            String sql = "UPDATE " + tableName + " SET animations_enabled = ? WHERE player_uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setBoolean(1, enabled);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    /**
     * Deletes a player's data from the database.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture that completes when deleted
     */
    public CompletableFuture<Void> delete(UUID uuid) {
        return dbManager.executeAsyncVoid(conn -> {
            String sql = "DELETE FROM " + tableName + " WHERE player_uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    /**
     * Checks if a player exists in the database.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture with true if exists
     */
    public CompletableFuture<Boolean> exists(UUID uuid) {
        return dbManager.executeAsync(conn -> {
            String sql = "SELECT 1 FROM " + tableName + " WHERE player_uuid = ? LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    /**
     * Gets the total number of players in the database.
     *
     * @return CompletableFuture with the count
     */
    public CompletableFuture<Integer> count() {
        return dbManager.executeAsync(conn -> {
            String sql = "SELECT COUNT(*) FROM " + tableName;

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        });
    }
}
