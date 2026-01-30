package com.okimc.edtoolsperks.config;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Central configuration manager for EdToolsPerks plugin.
 * Handles loading, saving, and reloading of all YAML configuration files.
 *
 * <p>Manages the following configurations:</p>
 * <ul>
 *   <li>config.yml - Database settings, animation config, defaults</li>
 *   <li>messages.yml - All player-facing text and localization</li>
 *   <li>perks.yml - Perk definitions with levels and boosts</li>
 *   <li>guis.yml - GUI layouts and item definitions</li>
 * </ul>
 *
 * @author Snopeyy
 * @version 2.0.0
 */
public class ConfigManager {

    private final JavaPlugin plugin;

    // Sub-configuration managers
    private MessagesConfig messagesConfig;
    private PerksConfig perksConfig;
    private GuiConfig guiConfig;

    // Main config values cached for quick access
    private FileConfiguration mainConfig;

    // Database settings
    private String databaseType;
    private String databaseHost;
    private int databasePort;
    private String databaseName;
    private String databaseUsername;
    private String databasePassword;
    private String sqliteFile;

    // Roll system settings
    private int pityThreshold;
    private String pityGuaranteedCategory;

    // Animation settings
    private int animationDurationTicks;
    private int animationTickRate;
    private boolean defaultAnimationsEnabled;

    // Currency settings
    private String rollCurrency;

    // Debug mode
    private boolean debug;

    // Placeholder fallback
    private String noPerkFallback;

    // Interaction settings
    private boolean toolInteractionEnabled;
    private String interactionMode;
    private boolean sendJoinMessage;

    // Booster settings
    private String boosterDisplayName;

    // Update tool settings
    private boolean updateToolEnabled;
    private String updateToolCommand;

    // Category configurations cache
    private final Map<String, CategoryConfig> categories = new HashMap<>();

    /**
     * Creates a new ConfigManager instance.
     *
     * @param plugin The main plugin instance
     */
    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads all configuration files.
     * Should be called during plugin enable.
     *
     * @return true if all configs loaded successfully
     */
    public boolean loadAll() {
        try {
            // Save defaults if not exist
            saveDefaultConfigs();

            // Load main config
            loadMainConfig();

            // Load sub-configurations
            this.messagesConfig = new MessagesConfig(plugin);
            this.messagesConfig.load();
            this.messagesConfig.setConfigManager(this); // Link for category access

            this.perksConfig = new PerksConfig(plugin);
            this.perksConfig.load();
            this.perksConfig.setConfigManager(this); // Link for category access

            this.guiConfig = new GuiConfig(plugin);
            this.guiConfig.load();

            plugin.getLogger().info("All configurations loaded successfully.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load configurations!", e);
            return false;
        }
    }

    /**
     * Reloads all configuration files.
     * Called by /perks reload command.
     *
     * @return true if reload was successful
     */
    public boolean reloadAll() {
        try {
            // Reload main config
            loadMainConfig();

            // Reload sub-configurations
            messagesConfig.reload();
            perksConfig.reload();
            guiConfig.reload();

            if (debug) {
                plugin.getLogger().info("[DEBUG] All configurations reloaded.");
            }

            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to reload configurations!", e);
            return false;
        }
    }

    /**
     * Saves default configuration files from resources if they don't exist.
     */
    private void saveDefaultConfigs() {
        saveResourceIfNotExists("config.yml");
        saveResourceIfNotExists("messages.yml");
        saveResourceIfNotExists("perks.yml");
        saveResourceIfNotExists("guis.yml");
    }

    /**
     * Saves a resource file if it doesn't exist in the plugin data folder.
     *
     * @param resourceName The name of the resource file
     */
    private void saveResourceIfNotExists(String resourceName) {
        File file = new File(plugin.getDataFolder(), resourceName);
        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
        }
    }

    /**
     * Loads the main config.yml and caches frequently accessed values.
     */
    private void loadMainConfig() {
        plugin.reloadConfig();
        this.mainConfig = plugin.getConfig();

        // Debug mode
        this.debug = mainConfig.getBoolean("debug", false);

        // Database settings
        this.databaseType = mainConfig.getString("database.type", "sqlite").toLowerCase();
        this.databaseHost = mainConfig.getString("database.host", "localhost");
        this.databasePort = mainConfig.getInt("database.port", 3306);
        this.databaseName = mainConfig.getString("database.name", "edtoolsperks");
        this.databaseUsername = mainConfig.getString("database.username", "root");
        this.databasePassword = mainConfig.getString("database.password", "");
        this.sqliteFile = mainConfig.getString("database.file", "edtoolsperks.db");

        // Roll system settings
        this.pityThreshold = mainConfig.getInt("rolls.pity-threshold", 500);
        this.pityGuaranteedCategory = mainConfig.getString("rolls.pity-guaranteed-category", "morada");

        // Animation settings
        this.animationDurationTicks = mainConfig.getInt("animation.duration-ticks", 60);
        this.animationTickRate = mainConfig.getInt("animation.tick-rate", 2);
        this.defaultAnimationsEnabled = mainConfig.getBoolean("animation.default-enabled", true);

        // Currency settings
        this.rollCurrency = mainConfig.getString("currency", "orbes");

        // Placeholder fallback
        this.noPerkFallback = mainConfig.getString("placeholder-no-perk", "Ninguna");

        // Interaction settings
        this.toolInteractionEnabled = mainConfig.getBoolean("interaction.enabled", true);
        this.interactionMode = mainConfig.getString("interaction.mode", "shift_right_click");
        this.sendJoinMessage = mainConfig.getBoolean("interaction.send-join-message", false);

        // Booster settings
        this.boosterDisplayName = mainConfig.getString("booster-display-name", "Perks");

        // Update tool settings
        this.updateToolEnabled = mainConfig.getBoolean("update-tool.enabled", true);
        this.updateToolCommand = mainConfig.getString("update-tool.command", "edtool enchant {player} update 0");

        // Load categories
        loadCategories();
    }

    /**
     * Loads category configurations from config.yml.
     * Categories define display-name, color, glass-material, and sound.
     */
    private void loadCategories() {
        categories.clear();

        ConfigurationSection categoriesSection = mainConfig.getConfigurationSection("categories");
        if (categoriesSection == null) {
            plugin.getLogger().warning("No categories section found in config.yml");
            return;
        }

        for (String key : categoriesSection.getKeys(false)) {
            ConfigurationSection catSection = categoriesSection.getConfigurationSection(key);
            if (catSection == null) continue;

            String displayName = catSection.getString("display-name", key);
            String color = catSection.getString("color", "&f");
            String glassMaterialStr = catSection.getString("glass-material", "WHITE_STAINED_GLASS_PANE");
            String soundStr = catSection.getString("sound", "ENTITY_EXPERIENCE_ORB_PICKUP");

            Material glassMaterial;
            try {
                glassMaterial = Material.valueOf(glassMaterialStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                glassMaterial = Material.WHITE_STAINED_GLASS_PANE;
            }

            Sound sound;
            try {
                sound = Sound.valueOf(soundStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
            }

            categories.put(key.toLowerCase(), new CategoryConfig(displayName, color, glassMaterial, sound));
        }

        if (debug) {
            plugin.getLogger().info("[DEBUG] Loaded " + categories.size() + " categories from config.yml");
        }
    }

    /**
     * Loads a custom YAML file from the plugin data folder.
     *
     * @param fileName The name of the file to load
     * @return The loaded FileConfiguration, or null if failed
     */
    public FileConfiguration loadYamlFile(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        // Merge with defaults from JAR if available
        InputStream defaultStream = plugin.getResource(fileName);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaultConfig);
        }

        return config;
    }

    /**
     * Saves a FileConfiguration to the specified file.
     *
     * @param config   The configuration to save
     * @param fileName The name of the file
     * @return true if saved successfully
     */
    public boolean saveYamlFile(FileConfiguration config, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save " + fileName, e);
            return false;
        }
    }

    // ==================== Getters ====================

    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }

    public PerksConfig getPerksConfig() {
        return perksConfig;
    }

    public GuiConfig getGuiConfig() {
        return guiConfig;
    }

    public FileConfiguration getMainConfig() {
        return mainConfig;
    }

    public boolean isDebug() {
        return debug;
    }

    public String getDatabaseType() {
        return databaseType;
    }

    public String getDatabaseHost() {
        return databaseHost;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public String getDatabaseUsername() {
        return databaseUsername;
    }

    public String getDatabasePassword() {
        return databasePassword;
    }

    public String getSqliteFile() {
        return sqliteFile;
    }

    public int getPityThreshold() {
        return pityThreshold;
    }

    public String getPityGuaranteedCategory() {
        return pityGuaranteedCategory;
    }

    public int getAnimationDurationTicks() {
        return animationDurationTicks;
    }

    public int getAnimationTickRate() {
        return animationTickRate;
    }

    public boolean isDefaultAnimationsEnabled() {
        return defaultAnimationsEnabled;
    }

    public String getRollCurrency() {
        return rollCurrency;
    }

    public String getNoPerkFallback() {
        return noPerkFallback;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public boolean isToolInteractionEnabled() {
        return toolInteractionEnabled;
    }

    public String getInteractionMode() {
        return interactionMode;
    }

    public boolean isSendJoinMessage() {
        return sendJoinMessage;
    }

    public String getBoosterDisplayName() {
        return boosterDisplayName;
    }

    public boolean isUpdateToolEnabled() {
        return updateToolEnabled;
    }

    public String getUpdateToolCommand() {
        return updateToolCommand;
    }

    /**
     * Gets the display name for a tool type.
     *
     * @param toolId The EdTools OmniTool ID
     * @return The display name from config, or the ID itself if not configured
     */
    public String getToolDisplayName(String toolId) {
        if (toolId == null) return "Unknown";
        return mainConfig.getString("tool-display-names." + toolId.toLowerCase(), toolId);
    }

    /**
     * Gets the display name for a boost/currency type.
     *
     * @param boostId The EdTools currency ID
     * @return The display name from config, or the ID itself if not configured
     */
    public String getBoostDisplayName(String boostId) {
        if (boostId == null) return "Unknown";
        return mainConfig.getString("boost-display-names." + boostId.toLowerCase(), boostId);
    }

    // ==================== Category Methods ====================

    /**
     * Gets a category configuration by key.
     *
     * @param categoryKey The category key (e.g., "comun", "rara")
     * @return The CategoryConfig, or a default if not found
     */
    public CategoryConfig getCategory(String categoryKey) {
        if (categoryKey == null) {
            return CategoryConfig.DEFAULT;
        }
        return categories.getOrDefault(categoryKey.toLowerCase(), CategoryConfig.DEFAULT);
    }

    /**
     * Gets the color code for a category.
     *
     * @param categoryKey The category key
     * @return The color code (e.g., "&a", "&#9945FF")
     */
    public String getCategoryColor(String categoryKey) {
        return getCategory(categoryKey).color();
    }

    /**
     * Gets the display name for a category.
     *
     * @param categoryKey The category key
     * @return The display name
     */
    public String getCategoryDisplayName(String categoryKey) {
        return getCategory(categoryKey).displayName();
    }

    /**
     * Gets the glass material for a category.
     *
     * @param categoryKey The category key
     * @return The Material for glass panes
     */
    public Material getCategoryGlassMaterial(String categoryKey) {
        return getCategory(categoryKey).glassMaterial();
    }

    /**
     * Gets the sound for a category.
     *
     * @param categoryKey The category key
     * @return The Sound to play
     */
    public Sound getCategorySound(String categoryKey) {
        return getCategory(categoryKey).sound();
    }

    /**
     * Gets all registered category keys.
     *
     * @return Set of category keys
     */
    public Set<String> getCategoryKeys() {
        return categories.keySet();
    }

    /**
     * Checks if a category exists.
     *
     * @param categoryKey The category key
     * @return true if the category is defined
     */
    public boolean hasCategory(String categoryKey) {
        return categoryKey != null && categories.containsKey(categoryKey.toLowerCase());
    }

    // ==================== Records ====================

    /**
     * Record representing a category's complete configuration.
     *
     * @param displayName   The display name shown in GUIs
     * @param color         The color code for the category
     * @param glassMaterial The glass pane material for animations
     * @param sound         The sound played when rolling this category
     */
    public record CategoryConfig(String displayName, String color, Material glassMaterial, Sound sound) {
        /** Default category config for unknown categories */
        public static final CategoryConfig DEFAULT = new CategoryConfig(
            "Unknown", "&f", Material.WHITE_STAINED_GLASS_PANE, Sound.ENTITY_EXPERIENCE_ORB_PICKUP
        );

        /**
         * Gets the color code translated.
         *
         * @return Translated color code
         */
        public String translatedColor() {
            return org.bukkit.ChatColor.translateAlternateColorCodes('&', color);
        }
    }
}
