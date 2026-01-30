package com.okimc.edtoolsperks.database;

import com.okimc.edtoolsperks.model.ActivePerk;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Repository for active perk CRUD operations.
 * Handles the edtoolsperks_perks table.
 *
 * <p>Each player can have at most one perk per tool type.
 * The primary key is (player_uuid, tool_type).</p>
 *
 * <p>All methods return CompletableFuture for async execution.</p>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class PerkRepository {

    private final DatabaseManager dbManager;
    private final String tableName;

    /**
     * Creates a new PerkRepository instance.
     *
     * @param dbManager The database manager
     */
    public PerkRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.tableName = dbManager.getPerksTable();
    }

    /**
     * Loads all active perks for a player.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture with map of tool type to ActivePerk
     */
    public CompletableFuture<Map<String, ActivePerk>> loadAll(UUID uuid) {
        return dbManager.executeAsync(conn -> {
            Map<String, ActivePerk> perks = new HashMap<>();

            String sql = "SELECT tool_type, perk_key, level FROM " + tableName +
                " WHERE player_uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String toolType = rs.getString("tool_type").toLowerCase();
                        String perkKey = rs.getString("perk_key").toLowerCase();
                        int level = rs.getInt("level");

                        ActivePerk perk = new ActivePerk(perkKey, toolType, level);
                        perks.put(toolType, perk);
                    }
                }
            }

            return perks;
        });
    }

    /**
     * Loads a specific perk for a player and tool type.
     *
     * @param uuid     The player's UUID
     * @param toolType The tool type
     * @return CompletableFuture with Optional ActivePerk
     */
    public CompletableFuture<Optional<ActivePerk>> load(UUID uuid, String toolType) {
        return dbManager.executeAsync(conn -> {
            String sql = "SELECT perk_key, level FROM " + tableName +
                " WHERE player_uuid = ? AND tool_type = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, toolType.toLowerCase());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String perkKey = rs.getString("perk_key").toLowerCase();
                        int level = rs.getInt("level");

                        return Optional.of(new ActivePerk(perkKey, toolType, level));
                    }
                }
            }

            return Optional.empty();
        });
    }

    /**
     * Saves or updates an active perk for a player.
     * Replaces any existing perk for the same tool type.
     *
     * @param uuid The player's UUID
     * @param perk The active perk to save
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> save(UUID uuid, ActivePerk perk) {
        return dbManager.executeAsyncVoid(conn -> {
            String sql;
            if (dbManager.isUsingSqlite()) {
                sql = "INSERT OR REPLACE INTO " + tableName +
                    " (player_uuid, tool_type, perk_key, level) VALUES (?, ?, ?, ?)";
            } else {
                sql = "INSERT INTO " + tableName +
                    " (player_uuid, tool_type, perk_key, level) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE perk_key = VALUES(perk_key), level = VALUES(level)";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, perk.getToolType().toLowerCase());
                ps.setString(3, perk.getPerkKey().toLowerCase());
                ps.setInt(4, perk.getLevel());

                ps.executeUpdate();
            }
        });
    }

    /**
     * Saves multiple perks for a player in a batch operation.
     *
     * @param uuid  The player's UUID
     * @param perks Collection of perks to save
     * @return CompletableFuture that completes when all saved
     */
    public CompletableFuture<Void> saveAll(UUID uuid, Collection<ActivePerk> perks) {
        if (perks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return dbManager.executeAsyncVoid(conn -> {
            String sql;
            if (dbManager.isUsingSqlite()) {
                sql = "INSERT OR REPLACE INTO " + tableName +
                    " (player_uuid, tool_type, perk_key, level) VALUES (?, ?, ?, ?)";
            } else {
                sql = "INSERT INTO " + tableName +
                    " (player_uuid, tool_type, perk_key, level) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE perk_key = VALUES(perk_key), level = VALUES(level)";
            }

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);

                for (ActivePerk perk : perks) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, perk.getToolType().toLowerCase());
                    ps.setString(3, perk.getPerkKey().toLowerCase());
                    ps.setInt(4, perk.getLevel());
                    ps.addBatch();
                }

                ps.executeBatch();
                conn.commit();
                conn.setAutoCommit(true);
            } catch (SQLException e) {
                conn.rollback();
                conn.setAutoCommit(true);
                throw e;
            }
        });
    }

    /**
     * Updates only the level of an existing perk.
     *
     * @param uuid     The player's UUID
     * @param toolType The tool type
     * @param level    The new level
     * @return CompletableFuture that completes when updated
     */
    public CompletableFuture<Void> updateLevel(UUID uuid, String toolType, int level) {
        return dbManager.executeAsyncVoid(conn -> {
            String sql = "UPDATE " + tableName +
                " SET level = ? WHERE player_uuid = ? AND tool_type = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, level);
                ps.setString(2, uuid.toString());
                ps.setString(3, toolType.toLowerCase());

                ps.executeUpdate();
            }
        });
    }

    /**
     * Deletes a specific perk for a player.
     *
     * @param uuid     The player's UUID
     * @param toolType The tool type
     * @return CompletableFuture that completes when deleted
     */
    public CompletableFuture<Void> delete(UUID uuid, String toolType) {
        return dbManager.executeAsyncVoid(conn -> {
            String sql = "DELETE FROM " + tableName +
                " WHERE player_uuid = ? AND tool_type = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, toolType.toLowerCase());

                ps.executeUpdate();
            }
        });
    }

    /**
     * Deletes all perks for a player.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture that completes when deleted
     */
    public CompletableFuture<Void> deleteAll(UUID uuid) {
        return dbManager.executeAsyncVoid(conn -> {
            String sql = "DELETE FROM " + tableName + " WHERE player_uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    /**
     * Checks if a player has a perk for a specific tool type.
     *
     * @param uuid     The player's UUID
     * @param toolType The tool type
     * @return CompletableFuture with true if perk exists
     */
    public CompletableFuture<Boolean> exists(UUID uuid, String toolType) {
        return dbManager.executeAsync(conn -> {
            String sql = "SELECT 1 FROM " + tableName +
                " WHERE player_uuid = ? AND tool_type = ? LIMIT 1";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, toolType.toLowerCase());

                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    /**
     * Counts the number of perks a player has.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture with the perk count
     */
    public CompletableFuture<Integer> countForPlayer(UUID uuid) {
        return dbManager.executeAsync(conn -> {
            String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE player_uuid = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }
            }
        });
    }

    /**
     * Gets statistics about perk usage across all players.
     *
     * @return CompletableFuture with map of perk_key to usage count
     */
    public CompletableFuture<Map<String, Integer>> getPerkUsageStats() {
        return dbManager.executeAsync(conn -> {
            Map<String, Integer> stats = new HashMap<>();

            String sql = "SELECT perk_key, COUNT(*) as count FROM " + tableName +
                " GROUP BY perk_key ORDER BY count DESC";

            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    String perkKey = rs.getString("perk_key");
                    int count = rs.getInt("count");
                    stats.put(perkKey, count);
                }
            }

            return stats;
        });
    }

    /**
     * Gets all players who have a specific perk.
     *
     * @param perkKey The perk key
     * @return CompletableFuture with set of player UUIDs
     */
    public CompletableFuture<Set<UUID>> getPlayersWithPerk(String perkKey) {
        return dbManager.executeAsync(conn -> {
            Set<UUID> players = new HashSet<>();

            String sql = "SELECT player_uuid FROM " + tableName + " WHERE perk_key = ?";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, perkKey.toLowerCase());

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                            players.add(uuid);
                        } catch (IllegalArgumentException ignored) {
                            // Skip invalid UUIDs
                        }
                    }
                }
            }

            return players;
        });
    }

    /**
     * Sets a perk for a player, replacing any existing perk for that tool.
     * This is a convenience method that combines delete + insert semantics.
     *
     * @param uuid    The player's UUID
     * @param perkKey The perk key
     * @param toolType The tool type
     * @param level   The perk level
     * @return CompletableFuture with the created ActivePerk
     */
    public CompletableFuture<ActivePerk> setPerk(UUID uuid, String perkKey, String toolType, int level) {
        ActivePerk perk = new ActivePerk(perkKey, toolType, level);
        return save(uuid, perk).thenApply(v -> perk);
    }
}
