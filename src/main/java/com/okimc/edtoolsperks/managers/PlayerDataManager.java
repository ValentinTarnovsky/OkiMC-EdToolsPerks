package com.okimc.edtoolsperks.managers;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.PerksConfig;
import com.okimc.edtoolsperks.database.DatabaseManager;
import com.okimc.edtoolsperks.model.ActivePerk;
import com.okimc.edtoolsperks.model.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Manages in-memory player data cache with async database synchronization.
 *
 * <p>This manager maintains a cache of PlayerData objects for online players,
 * handling automatic loading on join and saving on quit or when data changes.</p>
 *
 * <p>Key responsibilities:</p>
 * <ul>
 *   <li>Load player data asynchronously on join</li>
 *   <li>Cache data in memory for fast access</li>
 *   <li>Save changes to database asynchronously</li>
 *   <li>Link perk definitions after loading</li>
 *   <li>Clear cache on quit and plugin disable</li>
 * </ul>
 *
 * @author Snopeyy
 * @version 2.0.0
 */
public class PlayerDataManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final DatabaseManager databaseManager;
    private final PerksConfig perksConfig;

    // In-memory cache: UUID -> PlayerData
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    // Callbacks for when player data is loaded
    private final Map<UUID, List<Consumer<PlayerData>>> loadCallbacks = new ConcurrentHashMap<>();

    // Set of UUIDs currently being loaded (to prevent duplicate loads)
    private final Set<UUID> loading = ConcurrentHashMap.newKeySet();

    /**
     * Creates a new PlayerDataManager instance.
     *
     * @param plugin          The main plugin instance
     * @param configManager   The configuration manager
     * @param databaseManager The database manager
     */
    public PlayerDataManager(JavaPlugin plugin, ConfigManager configManager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.databaseManager = databaseManager;
        this.perksConfig = configManager.getPerksConfig();
    }

    /**
     * Loads player data asynchronously and caches it.
     * Called when a player joins the server.
     *
     * @param player The player who joined
     * @return CompletableFuture with the loaded PlayerData
     */
    public CompletableFuture<PlayerData> loadPlayer(Player player) {
        return loadPlayer(player.getUniqueId());
    }

    /**
     * Loads player data asynchronously by UUID.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture with the loaded PlayerData
     */
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        // Return cached data if already loaded
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Prevent duplicate loading
        if (!loading.add(uuid)) {
            // Already loading, return a future that will complete when loading finishes
            CompletableFuture<PlayerData> future = new CompletableFuture<>();
            loadCallbacks.computeIfAbsent(uuid, k -> new ArrayList<>())
                .add(future::complete);
            return future;
        }

        boolean defaultAnimations = configManager.isDefaultAnimationsEnabled();

        return databaseManager.getPlayerRepository()
            .loadOrCreate(uuid, defaultAnimations)
            .thenCompose(playerData -> {
                // Load perks for this player
                return databaseManager.getPerkRepository()
                    .loadAll(uuid)
                    .thenApply(perks -> {
                        // Add perks to player data and link definitions
                        for (ActivePerk perk : perks.values()) {
                            linkPerkDefinition(perk);
                            playerData.setPerk(perk);
                        }
                        playerData.markClean();
                        return playerData;
                    });
            })
            .whenComplete((data, error) -> {
                loading.remove(uuid);

                if (error != null) {
                    plugin.getLogger().log(Level.SEVERE,
                        "Failed to load player data for " + uuid, error);

                    // Notify waiting callbacks with null
                    notifyCallbacks(uuid, null);
                } else {
                    // Cache the data
                    cache.put(uuid, data);

                    if (configManager.isDebug()) {
                        plugin.getLogger().info("[DEBUG] Loaded data for " + uuid +
                            " (rolls=" + data.getRolls() + ", perks=" + data.getPerkCount() + ")");
                    }

                    // Notify waiting callbacks
                    notifyCallbacks(uuid, data);
                }
            });
    }

    /**
     * Notifies all waiting callbacks for a player.
     */
    private void notifyCallbacks(UUID uuid, PlayerData data) {
        List<Consumer<PlayerData>> callbacks = loadCallbacks.remove(uuid);
        if (callbacks != null) {
            for (Consumer<PlayerData> callback : callbacks) {
                try {
                    callback.accept(data);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                        "Error in player load callback for " + uuid, e);
                }
            }
        }
    }

    /**
     * Links a perk to its definition from config.
     *
     * @param perk The perk to link
     */
    private void linkPerkDefinition(ActivePerk perk) {
        if (perksConfig != null) {
            var definition = perksConfig.getPerk(perk.getPerkKey());
            if (definition != null) {
                perk.linkDefinition(definition);
            }
        }
    }

    /**
     * Gets cached player data if available.
     *
     * @param player The player
     * @return The PlayerData or null if not cached
     */
    public PlayerData getData(Player player) {
        return cache.get(player.getUniqueId());
    }

    /**
     * Gets cached player data by UUID.
     *
     * @param uuid The player's UUID
     * @return The PlayerData or null if not cached
     */
    public PlayerData getData(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Gets player data, loading if necessary.
     * This should be used when you need to ensure data is available.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture with the PlayerData
     */
    public CompletableFuture<PlayerData> getOrLoadData(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        return loadPlayer(uuid);
    }

    /**
     * Checks if player data is cached.
     *
     * @param uuid The player's UUID
     * @return true if data is in cache
     */
    public boolean isLoaded(UUID uuid) {
        return cache.containsKey(uuid);
    }

    /**
     * Checks if player data is currently being loaded.
     *
     * @param uuid The player's UUID
     * @return true if loading is in progress
     */
    public boolean isLoading(UUID uuid) {
        return loading.contains(uuid);
    }

    /**
     * Saves player data to the database asynchronously.
     *
     * @param data The player data to save
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> savePlayer(PlayerData data) {
        if (data == null) {
            return CompletableFuture.completedFuture(null);
        }

        UUID uuid = data.getUuid();

        // Save player base data
        CompletableFuture<Void> savePlayerFuture =
            databaseManager.getPlayerRepository().save(data);

        // Save perks
        CompletableFuture<Void> savePerksFuture =
            databaseManager.getPerkRepository().saveAll(uuid, data.getAllPerks().values());

        return CompletableFuture.allOf(savePlayerFuture, savePerksFuture)
            .whenComplete((v, error) -> {
                if (error != null) {
                    plugin.getLogger().log(Level.SEVERE,
                        "Failed to save player data for " + uuid, error);
                } else {
                    data.markClean();

                    if (configManager.isDebug()) {
                        plugin.getLogger().info("[DEBUG] Saved data for " + uuid);
                    }
                }
            });
    }

    /**
     * Saves player data if it has unsaved changes.
     *
     * @param data The player data
     * @return CompletableFuture that completes when saved (or immediately if clean)
     */
    public CompletableFuture<Void> saveIfDirty(PlayerData data) {
        if (data == null || !data.isDirty()) {
            return CompletableFuture.completedFuture(null);
        }
        return savePlayer(data);
    }

    /**
     * Unloads player data from cache and saves to database.
     * Called when a player quits the server.
     *
     * @param player The player who quit
     * @return CompletableFuture that completes when saved and removed
     */
    public CompletableFuture<Void> unloadPlayer(Player player) {
        return unloadPlayer(player.getUniqueId());
    }

    /**
     * Unloads player data from cache by UUID.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture that completes when saved and removed
     */
    public CompletableFuture<Void> unloadPlayer(UUID uuid) {
        PlayerData data = cache.remove(uuid);
        loading.remove(uuid);
        loadCallbacks.remove(uuid);

        if (data != null) {
            return savePlayer(data);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Saves all cached player data to the database.
     *
     * @return CompletableFuture that completes when all data is saved
     */
    public CompletableFuture<Void> saveAll() {
        if (cache.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (PlayerData data : cache.values()) {
            futures.add(savePlayer(data));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    /**
     * Clears all cached data without saving.
     * Use with caution - data may be lost!
     */
    public void clearCache() {
        cache.clear();
        loading.clear();
        loadCallbacks.clear();

        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] Player data cache cleared.");
        }
    }

    /**
     * Saves all data and clears the cache.
     * Called during plugin disable.
     *
     * @return CompletableFuture that completes when done
     */
    public CompletableFuture<Void> shutdown() {
        plugin.getLogger().info("Saving all player data...");

        return saveAll().whenComplete((v, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Error saving player data during shutdown!", error);
            }
            clearCache();
            plugin.getLogger().info("Player data manager shutdown complete.");
        });
    }

    /**
     * Updates a perk for a player and saves immediately.
     *
     * @param uuid The player's UUID
     * @param perk The new/updated perk
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> updatePerk(UUID uuid, ActivePerk perk) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            linkPerkDefinition(perk);
            data.setPerk(perk);
        }

        return databaseManager.getPerkRepository().save(uuid, perk);
    }

    /**
     * Removes a perk from a player and saves immediately.
     *
     * @param uuid     The player's UUID
     * @param toolType The tool type to remove perk from
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> removePerk(UUID uuid, String toolType) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            data.removePerk(toolType);
        }

        return databaseManager.getPerkRepository().delete(uuid, toolType);
    }

    /**
     * Adds rolls to a player and saves immediately.
     *
     * @param uuid   The player's UUID
     * @param amount The amount of rolls to add
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> addRolls(UUID uuid, int amount) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            data.addRolls(amount);
        }

        return databaseManager.getPlayerRepository().addRolls(uuid, amount);
    }

    /**
     * Sets the rolls for a player and saves immediately.
     *
     * @param uuid   The player's UUID
     * @param amount The new roll count
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> setRolls(UUID uuid, int amount) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            data.setRolls(amount);
        }

        return databaseManager.getPlayerRepository().setRolls(uuid, amount);
    }

    /**
     * Removes rolls from a player and saves immediately.
     *
     * @param uuid   The player's UUID
     * @param amount The amount of rolls to remove
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> removeRolls(UUID uuid, int amount) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            data.removeRolls(amount);
        }

        return databaseManager.getPlayerRepository().addRolls(uuid, -amount);
    }

    /**
     * Saves a perk for a player immediately.
     *
     * @param uuid The player's UUID
     * @param perk The perk to save
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> savePerk(UUID uuid, ActivePerk perk) {
        return updatePerk(uuid, perk);
    }

    /**
     * Resets a player's pity counter.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> resetPity(UUID uuid) {
        return resetPityCount(uuid);
    }

    /**
     * Updates the pity count for a player.
     *
     * @param uuid      The player's UUID
     * @param pityCount The new pity count
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> updatePityCount(UUID uuid, int pityCount) {
        PlayerData data = cache.get(uuid);
        if (data != null) {
            data.setPityCount(pityCount);
        }

        return databaseManager.getPlayerRepository().updatePityCount(uuid, pityCount);
    }

    /**
     * Resets a player's pity count to zero.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture that completes when saved
     */
    public CompletableFuture<Void> resetPityCount(UUID uuid) {
        return updatePityCount(uuid, 0);
    }

    /**
     * Toggles animations for a player and saves.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture with the new animation state
     */
    public CompletableFuture<Boolean> toggleAnimations(UUID uuid) {
        PlayerData data = cache.get(uuid);
        boolean newState = true;

        if (data != null) {
            newState = data.toggleAnimations();
        }

        final boolean finalState = newState;
        return databaseManager.getPlayerRepository()
            .updateAnimationsEnabled(uuid, newState)
            .thenApply(v -> finalState);
    }

    /**
     * Relinks all perk definitions after a config reload.
     * Called when /perks reload is executed.
     */
    public void relinkPerkDefinitions() {
        for (PlayerData data : cache.values()) {
            for (ActivePerk perk : data.getAllPerks().values()) {
                linkPerkDefinition(perk);
            }
        }

        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] Relinked perk definitions for " +
                cache.size() + " cached players.");
        }
    }

    /**
     * Gets the number of players in the cache.
     *
     * @return The cache size
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Gets all cached player UUIDs.
     *
     * @return Unmodifiable set of cached UUIDs
     */
    public Set<UUID> getCachedPlayers() {
        return Collections.unmodifiableSet(cache.keySet());
    }
}
