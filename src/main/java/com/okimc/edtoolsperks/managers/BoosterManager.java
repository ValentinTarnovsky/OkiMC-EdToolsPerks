package com.okimc.edtoolsperks.managers;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.integration.EdToolsIntegration;
import com.okimc.edtoolsperks.model.ActivePerk;
import es.edwardbelt.edgens.iapi.EdToolsBoostersAPI;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages EdTools boosters for player perks.
 * Handles applying and removing boost multipliers based on active perks.
 *
 * <p>This manager tracks active boosters per player and ensures they are
 * properly applied when perks are assigned and removed when perks change.</p>
 *
 * <p>Supports two types of boosters:</p>
 * <ul>
 *   <li><b>Currency Boosters</b>: Use EdTools currency IDs (orbes, farm-coins, etc.)</li>
 *   <li><b>Enchantment Boosters</b>: Use "enchants" keyword to boost global enchantments</li>
 * </ul>
 *
 * @author Snopeyy
 * @version 2.0.0
 */
public class BoosterManager {

    private static final String BOOSTER_PREFIX = "edtoolsperks";

    // Very long duration for "permanent" boosters (100 years in seconds)
    private static final long PERMANENT_DURATION = 100L * 365L * 24L * 60L * 60L;

    // Special keyword for enchantment boosters (global enchant multiplier)
    private static final String ENCHANTS_KEYWORD = "enchants";

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final EdToolsIntegration edToolsIntegration;

    // Track active boosters: playerUUID -> (boosterId -> BoosterData)
    private final Map<UUID, Map<String, BoosterData>> activeBoosters = new ConcurrentHashMap<>();

    // Display names for boost types (loaded from config)
    private final Map<String, String> boostDisplayNames = new HashMap<>();

    // Display name for boosters in EdTools booster list (configurable)
    private String boosterDisplayName;

    /**
     * Data class to track booster information.
     */
    private record BoosterData(String boostType, String economyName, double multiplier) {}

    /**
     * Creates a new BoosterManager instance.
     *
     * @param plugin             The main plugin instance
     * @param configManager      The configuration manager
     * @param edToolsIntegration The EdTools integration
     */
    public BoosterManager(JavaPlugin plugin, ConfigManager configManager,
                          EdToolsIntegration edToolsIntegration) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.edToolsIntegration = edToolsIntegration;

        loadBoostDisplayNames();
    }

    /**
     * Loads boost display names from config.
     */
    private void loadBoostDisplayNames() {
        boostDisplayNames.clear();

        // Load display names from config
        var configSection = configManager.getMainConfig()
            .getConfigurationSection("boost-display-names");

        if (configSection != null) {
            for (String key : configSection.getKeys(false)) {
                String value = configSection.getString(key);
                if (value != null) {
                    boostDisplayNames.put(key.toLowerCase(), value);
                }
            }
        }

        // Load booster display name from config
        this.boosterDisplayName = configManager.getMainConfig()
            .getString("booster-display-name", "Perks");

        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] Loaded " + boostDisplayNames.size() +
                " boost display names.");
            plugin.getLogger().info("[DEBUG] Booster display name: " + boosterDisplayName);
        }
    }

    /**
     * Reloads the boost display names from config.
     */
    public void reload() {
        loadBoostDisplayNames();
    }

    /**
     * Refreshes boosters for all online players.
     * Called after config reload to apply updated boost values.
     *
     * <p>This method re-applies all active perks with their current config values,
     * ensuring that any changes to boost percentages are reflected immediately
     * without requiring players to reconnect.</p>
     *
     * @param playerDataManager The player data manager to get cached player data
     */
    public void refreshAllOnlinePlayers(PlayerDataManager playerDataManager) {
        if (playerDataManager == null) return;

        int refreshedCount = 0;
        int boostersUpdated = 0;

        for (UUID uuid : playerDataManager.getCachedPlayers()) {
            var playerData = playerDataManager.getData(uuid);
            if (playerData == null) continue;

            Map<String, com.okimc.edtoolsperks.model.ActivePerk> perks = playerData.getAllPerks();
            if (perks.isEmpty()) continue;

            // Get the online player (needed for applyBoosters)
            org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;

            refreshedCount++;

            // Re-apply each perk's boosters with current config values
            for (var perk : perks.values()) {
                // The perk definition should already be relinked by relinkPerkDefinitions()
                // Now just re-apply the boosters which will use the new values
                applyBoosters(player, perk);
                boostersUpdated += perk.getBoostMap().size();
            }
        }

        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] Refreshed boosters for " + refreshedCount +
                " online players (" + boostersUpdated + " boosters updated).");
        }
    }

    /**
     * Applies all boosters for a perk to a player.
     * Issue #5 fix: Ensures proper cleanup and prevents double application.
     *
     * @param player The player
     * @param perk   The active perk
     */
    public void applyBoosters(Player player, ActivePerk perk) {
        if (player == null || perk == null) return;

        UUID uuid = player.getUniqueId();
        String toolType = perk.getToolType();

        // First remove any existing boosters for this tool type (from local tracking)
        removeBoostersForTool(player, toolType);

        // Get boost data from perk
        Map<String, Double> boosts = perk.getBoostMap();
        if (boosts.isEmpty()) {
            if (configManager.isDebug()) {
                plugin.getLogger().info("[DEBUG] No boosts defined for perk: " + perk.getPerkKey());
            }
            return;
        }

        // Apply each boost
        for (Map.Entry<String, Double> entry : boosts.entrySet()) {
            String boostType = entry.getKey();  // This is the EdTools currency ID directly
            double boostPercent = entry.getValue();

            String boosterId = generateBoosterId(uuid, toolType, boostType);
            double multiplier = boostPercent / 100.0;

            // Issue #5: Safety check - ensure booster is removed from EdTools API
            // even if not in local tracking (e.g., from a previous session that crashed)
            safeRemoveBoosterFromApi(uuid, boosterId);

            // Use boostType directly as economy name (no mapping needed)
            boolean applied = applyBooster(uuid, boosterId, boostType, boostType, multiplier);

            if (applied) {
                // Track the active booster with its data
                // boostType is used as both the type and economy name (they're the same - the EdTools currency ID)
                activeBoosters.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                    .put(boosterId, new BoosterData(boostType, boostType, multiplier));

                if (configManager.isDebug()) {
                    plugin.getLogger().info("[DEBUG] Applied booster " + boosterId +
                        " (+" + boostPercent + "% " + boostType + ") to " + player.getName());
                }
            }
        }
    }

    /**
     * Safely removes a booster from EdTools API if it exists.
     * Used to prevent double-application issues.
     *
     * @param uuid      The player's UUID
     * @param boosterId The unique booster ID
     */
    private void safeRemoveBoosterFromApi(UUID uuid, String boosterId) {
        try {
            EdToolsBoostersAPI boostersAPI = edToolsIntegration.getBoostersAPI();
            if (boostersAPI != null && boostersAPI.existsBooster(uuid, boosterId)) {
                boostersAPI.removeBooster(uuid, boosterId);
                if (configManager.isDebug()) {
                    plugin.getLogger().info("[DEBUG] Pre-cleared existing booster from API: " + boosterId);
                }
            }
        } catch (Exception e) {
            // Silently ignore - this is just a safety measure
            if (configManager.isDebug()) {
                plugin.getLogger().warning("[DEBUG] Could not pre-clear booster: " + boosterId);
            }
        }
    }

    /**
     * Removes all boosters for a specific tool type from a player.
     * Issue #5 fix: Enhanced to also clean up from EdTools API for boosters
     * that may exist in API but not in local tracking.
     *
     * @param player   The player
     * @param toolType The tool type
     */
    public void removeBoostersForTool(Player player, String toolType) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        Map<String, BoosterData> playerBoosters = activeBoosters.get(uuid);

        // Find and remove boosters for this tool type from local tracking
        String prefix = BOOSTER_PREFIX + "-" + uuid.toString() + "-" + toolType.toLowerCase();

        if (playerBoosters != null && !playerBoosters.isEmpty()) {
            List<String> toRemove = new ArrayList<>();
            for (String boosterId : playerBoosters.keySet()) {
                if (boosterId.startsWith(prefix)) {
                    toRemove.add(boosterId);
                }
            }

            for (String boosterId : toRemove) {
                BoosterData data = playerBoosters.remove(boosterId);
                removeBooster(uuid, boosterId, data);

                if (configManager.isDebug()) {
                    plugin.getLogger().info("[DEBUG] Removed booster " + boosterId +
                        " from " + player.getName());
                }
            }

            // Clean up empty map
            if (playerBoosters.isEmpty()) {
                activeBoosters.remove(uuid);
            }
        }

        // Issue #5: Also try to clean up any orphan boosters from EdTools API
        // that might exist but aren't in our local tracking
        cleanupOrphanBoostersFromApi(uuid, toolType);
    }

    /**
     * Attempts to clean up any orphan boosters from EdTools API that match
     * our naming pattern but aren't in local tracking.
     *
     * @param uuid     The player's UUID
     * @param toolType The tool type
     */
    private void cleanupOrphanBoostersFromApi(UUID uuid, String toolType) {
        try {
            EdToolsBoostersAPI boostersAPI = edToolsIntegration.getBoostersAPI();
            if (boostersAPI == null) return;

            // Try common boost types that might have orphan boosters (including enchants)
            String[] commonBoostTypes = {"orbes", "coins", "farm-coins", "pases", "esencias", "experiencia", "exp", "enchants", "enchant", "global-enchants"};
            String prefix = BOOSTER_PREFIX + "-" + uuid.toString() + "-" + toolType.toLowerCase();

            for (String boostType : commonBoostTypes) {
                String potentialBoosterId = prefix + "-" + boostType.toLowerCase();
                if (boostersAPI.existsBooster(uuid, potentialBoosterId)) {
                    boostersAPI.removeBooster(uuid, potentialBoosterId);
                    if (configManager.isDebug()) {
                        plugin.getLogger().info("[DEBUG] Cleaned up orphan booster from API: " + potentialBoosterId);
                    }
                }
            }
        } catch (Exception e) {
            // Silently ignore - this is just a cleanup measure
            if (configManager.isDebug()) {
                plugin.getLogger().warning("[DEBUG] Could not cleanup orphan boosters for tool: " + toolType);
            }
        }
    }

    /**
     * Removes all boosters from a player.
     *
     * @param player The player
     */
    public void removeAllBoosters(Player player) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        Map<String, BoosterData> playerBoosters = activeBoosters.remove(uuid);

        if (playerBoosters == null || playerBoosters.isEmpty()) {
            return;
        }

        for (Map.Entry<String, BoosterData> entry : playerBoosters.entrySet()) {
            removeBooster(uuid, entry.getKey(), entry.getValue());
        }

        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] Removed all boosters from " + player.getName());
        }
    }

    /**
     * Removes all boosters from a player by UUID.
     *
     * @param uuid The player's UUID
     */
    public void removeAllBoosters(UUID uuid) {
        Map<String, BoosterData> playerBoosters = activeBoosters.remove(uuid);

        if (playerBoosters == null || playerBoosters.isEmpty()) {
            return;
        }

        for (Map.Entry<String, BoosterData> entry : playerBoosters.entrySet()) {
            removeBooster(uuid, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Applies a booster using the EdTools Boosters API.
     * Falls back to local tracking if API is unavailable.
     *
     * <p>Supports two booster types:</p>
     * <ul>
     *   <li><b>Currency Boosters</b>: Normal economy/currency multipliers</li>
     *   <li><b>Enchantment Boosters</b>: Global enchantment multipliers (use "enchants" as boostType)</li>
     * </ul>
     *
     * @param uuid        The player's UUID
     * @param boosterId   The unique booster ID
     * @param boostType   The type of boost ("enchants" for enchantment booster, or currency ID)
     * @param economyName The EdTools economy name (ignored for enchant boosters)
     * @param multiplier  The multiplier (e.g., 0.10 for 10%)
     * @return true if applied successfully
     */
    private boolean applyBooster(UUID uuid, String boosterId, String boostType, String economyName, double multiplier) {
        try {
            // Try to use EdTools Boosters API if available
            EdToolsBoostersAPI boostersAPI = edToolsIntegration.getBoostersAPI();

            if (boostersAPI != null) {
                // Check if this is an enchantment booster
                boolean isEnchantBooster = isEnchantBoostType(boostType);

                // Use EdTools Boosters API
                // Parameters: uuid, boosterId, boosterName, economy, multiplier, duration, enchantBooster, saveDB
                // Duration is set to a very long time since perks are "permanent" until changed
                // saveDB is false since we manage perk persistence ourselves
                boostersAPI.addBooster(
                    uuid,
                    boosterId,
                    boosterDisplayName,                          // Display name shown in EdTools booster list
                    isEnchantBooster ? "" : economyName,         // Empty economy for enchant boosters
                    multiplier,                                  // Multiplier value (0.10 = +10%)
                    PERMANENT_DURATION,                          // Duration in seconds (effectively permanent)
                    isEnchantBooster,                            // true for enchant booster, false for currency
                    false                                        // Don't save to DB (we manage persistence)
                );

                if (configManager.isDebug()) {
                    String boostTypeDesc = isEnchantBooster ? "ENCHANTS" : economyName;
                    plugin.getLogger().info("[DEBUG] Applied " + (isEnchantBooster ? "ENCHANT" : "CURRENCY") +
                        " booster via EdTools API: " + boosterId +
                        " (+" + (multiplier * 100) + "% " + boostTypeDesc + ") for player " + uuid);
                }
            } else {
                // Fallback: track locally if API not available
                if (configManager.isDebug()) {
                    plugin.getLogger().info("[DEBUG] EdTools Boosters API not available. " +
                        "Booster tracked locally: " + boosterId);
                }
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                "Failed to apply booster " + boosterId, e);
            return false;
        }
    }

    /**
     * Checks if a boost type is an enchantment booster.
     * Enchantment boosters use the "enchants" keyword (case-insensitive).
     *
     * @param boostType The boost type to check
     * @return true if this is an enchantment booster type
     */
    public boolean isEnchantBoostType(String boostType) {
        if (boostType == null) return false;
        String normalized = boostType.toLowerCase().trim();
        return normalized.equals(ENCHANTS_KEYWORD) ||
               normalized.equals("enchant") ||
               normalized.equals("global-enchants") ||
               normalized.equals("encantamientos");
    }

    /**
     * Removes a booster using the EdTools Boosters API.
     *
     * @param uuid      The player's UUID
     * @param boosterId The unique booster ID
     * @param data      The booster data
     */
    private void removeBooster(UUID uuid, String boosterId, BoosterData data) {
        try {
            // Try to remove via EdTools Boosters API if available
            EdToolsBoostersAPI boostersAPI = edToolsIntegration.getBoostersAPI();

            if (boostersAPI != null) {
                // Check if booster exists before trying to remove
                if (boostersAPI.existsBooster(uuid, boosterId)) {
                    boostersAPI.removeBooster(uuid, boosterId);

                    if (configManager.isDebug()) {
                        plugin.getLogger().info("[DEBUG] Removed booster via EdTools API: " + boosterId);
                    }
                }
            }

            if (configManager.isDebug() && data != null) {
                plugin.getLogger().info("[DEBUG] Booster removed from tracking: " + boosterId +
                    " (" + data.boostType() + " -> " + data.economyName() + ")");
            }

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                "Failed to remove booster " + boosterId, e);
        }
    }

    /**
     * Generates a unique booster ID.
     *
     * @param uuid      The player's UUID
     * @param toolType  The tool type
     * @param boostType The boost type
     * @return The unique booster ID
     */
    private String generateBoosterId(UUID uuid, String toolType, String boostType) {
        return BOOSTER_PREFIX + "-" + uuid.toString() + "-" +
            toolType.toLowerCase() + "-" + boostType.toLowerCase();
    }

    /**
     * Gets the display name for a boost/currency type.
     *
     * @param boostType The EdTools currency ID
     * @return The display name from config, or the ID itself if not configured
     */
    public String getBoostDisplayName(String boostType) {
        return boostDisplayNames.getOrDefault(
            boostType.toLowerCase(),
            boostType
        );
    }

    /**
     * Gets the total boost multiplier for a player and boost type.
     * Used by listeners to apply boosts at currency earn time.
     *
     * @param uuid      The player's UUID
     * @param boostType The EdTools currency ID
     * @return The total multiplier (1.0 = no boost, 1.1 = 10% boost)
     */
    public double getTotalMultiplier(UUID uuid, String boostType) {
        Map<String, BoosterData> playerBoosters = activeBoosters.get(uuid);
        if (playerBoosters == null || playerBoosters.isEmpty()) {
            return 1.0;
        }

        String targetType = boostType.toLowerCase();

        // Sum all active boosts for this type (additive stacking)
        double totalBoost = 0.0;
        for (BoosterData data : playerBoosters.values()) {
            if (data.boostType().equalsIgnoreCase(targetType) ||
                data.economyName().equalsIgnoreCase(targetType)) {
                totalBoost += data.multiplier();
            }
        }

        return 1.0 + totalBoost;
    }

    /**
     * Gets the total boost multiplier for a player, economy name, and tool type.
     * More specific version for tool-based boost lookup.
     *
     * @param uuid        The player's UUID
     * @param economyName The EdTools economy name
     * @param toolType    The tool type (or null for all tools)
     * @return The total multiplier (1.0 = no boost, 1.1 = 10% boost)
     */
    public double getTotalMultiplierForTool(UUID uuid, String economyName, String toolType) {
        Map<String, BoosterData> playerBoosters = activeBoosters.get(uuid);
        if (playerBoosters == null || playerBoosters.isEmpty()) {
            return 1.0;
        }

        String prefix = (toolType != null)
            ? BOOSTER_PREFIX + "-" + uuid.toString() + "-" + toolType.toLowerCase()
            : BOOSTER_PREFIX + "-" + uuid.toString();

        double totalBoost = 0.0;
        for (Map.Entry<String, BoosterData> entry : playerBoosters.entrySet()) {
            if (entry.getKey().startsWith(prefix) &&
                entry.getValue().economyName().equalsIgnoreCase(economyName)) {
                totalBoost += entry.getValue().multiplier();
            }
        }

        return 1.0 + totalBoost;
    }

    /**
     * Checks if a player has any active boosters.
     *
     * @param uuid The player's UUID
     * @return true if player has active boosters
     */
    public boolean hasActiveBoosters(UUID uuid) {
        Map<String, BoosterData> playerBoosters = activeBoosters.get(uuid);
        return playerBoosters != null && !playerBoosters.isEmpty();
    }

    /**
     * Gets the count of active boosters for a player.
     *
     * @param uuid The player's UUID
     * @return The number of active boosters
     */
    public int getBoosterCount(UUID uuid) {
        Map<String, BoosterData> playerBoosters = activeBoosters.get(uuid);
        return playerBoosters != null ? playerBoosters.size() : 0;
    }

    /**
     * Gets a summary of active boosts for a player.
     * Useful for displaying boost information in GUIs.
     *
     * @param uuid The player's UUID
     * @return Map of economy name to total boost percentage
     */
    public Map<String, Double> getActiveBoostSummary(UUID uuid) {
        Map<String, BoosterData> playerBoosters = activeBoosters.get(uuid);
        if (playerBoosters == null || playerBoosters.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Double> summary = new HashMap<>();
        for (BoosterData data : playerBoosters.values()) {
            summary.merge(data.economyName(), data.multiplier() * 100, Double::sum);
        }
        return summary;
    }

    /**
     * Clears all tracked boosters.
     * Called during plugin disable.
     */
    public void clearAll() {
        // Remove all boosters from all players
        for (UUID uuid : new HashSet<>(activeBoosters.keySet())) {
            removeAllBoosters(uuid);
        }
        activeBoosters.clear();

        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] Cleared all tracked boosters.");
        }
    }

    /**
     * Gets the boost display names map.
     *
     * @return Unmodifiable map of currency ID to display name
     */
    public Map<String, String> getBoostDisplayNames() {
        return Collections.unmodifiableMap(boostDisplayNames);
    }
}
