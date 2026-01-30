package com.okimc.edtoolsperks.placeholders;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.MessagesConfig;
import com.okimc.edtoolsperks.managers.PlayerDataManager;
import com.okimc.edtoolsperks.model.ActivePerk;
import com.okimc.edtoolsperks.model.PlayerData;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * PlaceholderAPI expansion for EdToolsPerks.
 *
 * <p>Available placeholders:</p>
 * <ul>
 *   <li>%edtoolsperks_rolls% - Player's available rolls</li>
 *   <li>%edtoolsperks_pity% - Current pity counter</li>
 *   <li>%edtoolsperks_pity_threshold% - Pity threshold from config</li>
 *   <li>%edtoolsperks_pity_progress% - Pity progress (count/threshold)</li>
 *   <li>%edtoolsperks_perk_count% - Number of active perks</li>
 *   <li>%edtoolsperks_animations% - Whether animations are enabled</li>
 *   <li>%edtoolsperks_perk_&lt;tool&gt;% - Perk name for tool type</li>
 *   <li>%edtoolsperks_perk_&lt;tool&gt;_level% - Perk level for tool type</li>
 *   <li>%edtoolsperks_perk_&lt;tool&gt;_category% - Perk category for tool type</li>
 *   <li>%edtoolsperks_perk_&lt;tool&gt;_boost% - Perk boost description for tool type</li>
 *   <li>%edtoolsperks_has_perk_&lt;tool&gt;% - true/false if has perk for tool</li>
 * </ul>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class PerksPlaceholderExpansion extends PlaceholderExpansion {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final MessagesConfig messagesConfig;

    /**
     * Creates a new PerksPlaceholderExpansion.
     *
     * @param plugin            The main plugin instance
     * @param configManager     The configuration manager
     * @param playerDataManager The player data manager
     */
    public PerksPlaceholderExpansion(JavaPlugin plugin, ConfigManager configManager,
                                      PlayerDataManager playerDataManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.messagesConfig = configManager.getMessagesConfig();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "edtoolsperks";
    }

    @Override
    public @NotNull String getAuthor() {
        return "OkiMC";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Don't unregister on plugin reload
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        // Get player data from cache (only works for online players)
        PlayerData data = playerDataManager.getData(player.getUniqueId());

        // Handle placeholders that don't require player data
        if (params.equalsIgnoreCase("pity_threshold")) {
            return String.valueOf(configManager.getPityThreshold());
        }

        // If no data, return fallback
        if (data == null) {
            return getFallbackValue(params);
        }

        // Parse the placeholder
        String paramsLower = params.toLowerCase();

        // Basic placeholders
        switch (paramsLower) {
            case "rolls" -> {
                return String.valueOf(data.getRolls());
            }
            case "pity" -> {
                return String.valueOf(data.getPityCount());
            }
            case "pity_progress" -> {
                return data.getPityCount() + "/" + configManager.getPityThreshold();
            }
            case "pity_percent" -> {
                double percent = (double) data.getPityCount() / configManager.getPityThreshold() * 100;
                return String.format("%.1f", Math.min(100, percent));
            }
            case "perk_count" -> {
                return String.valueOf(data.getPerkCount());
            }
            case "animations" -> {
                return data.isAnimationsEnabled() ?
                    messagesConfig.get("placeholders.enabled") :
                    messagesConfig.get("placeholders.disabled");
            }
            case "animations_raw" -> {
                return String.valueOf(data.isAnimationsEnabled());
            }
        }

        // Tool-specific placeholders: perk_<tool>, perk_<tool>_level, etc.
        if (paramsLower.startsWith("perk_")) {
            return handlePerkPlaceholder(data, paramsLower.substring(5));
        }

        // Has perk check: has_perk_<tool>
        if (paramsLower.startsWith("has_perk_")) {
            String toolType = paramsLower.substring(9);
            return String.valueOf(data.hasPerk(toolType));
        }

        // Boost placeholders: boost_<tool>_<type>
        if (paramsLower.startsWith("boost_")) {
            return handleBoostPlaceholder(data, paramsLower.substring(6));
        }

        return null; // Unknown placeholder
    }

    /**
     * Handles perk-related placeholders.
     *
     * @param data   The player data
     * @param params The parameter after "perk_"
     * @return The placeholder value
     */
    private String handlePerkPlaceholder(PlayerData data, String params) {
        // Check for suffixes: _level, _category, _boost
        String toolType;
        String suffix = "";

        if (params.endsWith("_level")) {
            toolType = params.substring(0, params.length() - 6);
            suffix = "level";
        } else if (params.endsWith("_category")) {
            toolType = params.substring(0, params.length() - 9);
            suffix = "category";
        } else if (params.endsWith("_boost")) {
            toolType = params.substring(0, params.length() - 6);
            suffix = "boost";
        } else if (params.endsWith("_color")) {
            toolType = params.substring(0, params.length() - 6);
            suffix = "color";
        } else {
            // Just the perk name
            toolType = params;
            suffix = "name";
        }

        ActivePerk perk = data.getPerk(toolType);

        if (perk == null) {
            return getNoPerkFallback(suffix);
        }

        return switch (suffix) {
            case "name" -> {
                String color = messagesConfig.getCategoryColor(perk.getCategory());
                yield color + perk.getDisplayName();
            }
            case "level" -> String.valueOf(perk.getLevel());
            case "category" -> {
                String color = messagesConfig.getCategoryColor(perk.getCategory());
                yield color + messagesConfig.getCategoryName(perk.getCategory());
            }
            case "boost" -> {
                if (perk.hasDefinition()) {
                    var levelData = perk.getCurrentLevelData();
                    if (levelData != null) {
                        yield levelData.getBoostDescription();
                    }
                }
                yield configManager.getNoPerkFallback();
            }
            case "color" -> messagesConfig.getCategoryColor(perk.getCategory());
            default -> configManager.getNoPerkFallback();
        };
    }

    /**
     * Handles boost-related placeholders.
     * Format: boost_<tool>_<boostType>
     *
     * @param data   The player data
     * @param params The parameter after "boost_"
     * @return The boost value or "0"
     */
    private String handleBoostPlaceholder(PlayerData data, String params) {
        // Split into tool type and boost type
        int lastUnderscore = params.lastIndexOf('_');
        if (lastUnderscore == -1) {
            return "0";
        }

        String toolType = params.substring(0, lastUnderscore);
        String boostType = params.substring(lastUnderscore + 1);

        ActivePerk perk = data.getPerk(toolType);
        if (perk == null || !perk.hasDefinition()) {
            return "0";
        }

        Map<String, Double> boosts = perk.getBoostMap();
        Double value = boosts.get(boostType.toLowerCase());

        if (value == null) {
            // Try common variations
            value = boosts.get(boostType);
            if (value == null) {
                return "0";
            }
        }

        // Format as integer if whole number, otherwise with decimals
        if (value == Math.floor(value)) {
            return String.valueOf(value.intValue());
        }
        return String.format("%.1f", value);
    }

    /**
     * Gets a fallback value from config with a default.
     */
    private String getConfigFallback(String key, String defaultValue) {
        return messagesConfig.get("placeholders.fallback." + key, defaultValue);
    }

    /**
     * Gets a fallback value for when player data is not available.
     */
    private String getFallbackValue(String params) {
        String paramsLower = params.toLowerCase();

        if (paramsLower.equals("rolls") || paramsLower.equals("pity") ||
            paramsLower.equals("perk_count")) {
            return getConfigFallback("zero", "0");
        }

        if (paramsLower.equals("pity_progress")) {
            return getConfigFallback("zero", "0") + "/" + configManager.getPityThreshold();
        }

        if (paramsLower.equals("pity_percent")) {
            return getConfigFallback("zero-decimal", "0.0");
        }

        if (paramsLower.equals("animations") || paramsLower.equals("animations_raw")) {
            return messagesConfig.get("placeholders.unknown");
        }

        if (paramsLower.startsWith("perk_") || paramsLower.startsWith("has_perk_")) {
            return configManager.getNoPerkFallback();
        }

        if (paramsLower.startsWith("boost_")) {
            return getConfigFallback("zero", "0");
        }

        return "";
    }

    /**
     * Gets the fallback for when no perk is equipped.
     */
    private String getNoPerkFallback(String suffix) {
        return switch (suffix) {
            case "level" -> getConfigFallback("zero", "0");
            case "boost" -> getConfigFallback("zero-percent", "0%");
            case "color" -> "";
            default -> configManager.getNoPerkFallback();
        };
    }
}
