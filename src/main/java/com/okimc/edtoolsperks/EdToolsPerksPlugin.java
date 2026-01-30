package com.okimc.edtoolsperks;

import com.okimc.edtoolsperks.commands.PerksCommand;
import com.okimc.edtoolsperks.commands.PerksTabCompleter;
import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.database.DatabaseManager;
import com.okimc.edtoolsperks.gui.GuiManager;
import com.okimc.edtoolsperks.integration.EdToolsIntegration;
import com.okimc.edtoolsperks.listeners.InventoryClickListener;
import com.okimc.edtoolsperks.listeners.PerkEffectListener;
import com.okimc.edtoolsperks.listeners.PlayerConnectionListener;
import com.okimc.edtoolsperks.listeners.ToolInteractListener;
import com.okimc.edtoolsperks.managers.BoosterManager;
import com.okimc.edtoolsperks.managers.PerkManager;
import com.okimc.edtoolsperks.managers.PlayerDataManager;
import com.okimc.edtoolsperks.placeholders.PerksPlaceholderExpansion;
import com.okimc.edtoolsperks.utils.SkullUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Main plugin class for EdToolsPerks.
 *
 * <p>EdToolsPerks is a per-player perk system for EdTools that provides:</p>
 * <ul>
 *   <li>Gacha-style perk rolling with pity system</li>
 *   <li>Per-tool perks with configurable boosts</li>
 *   <li>Animated roll GUI</li>
 *   <li>PlaceholderAPI integration</li>
 *   <li>Async database operations (MySQL/SQLite)</li>
 * </ul>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class EdToolsPerksPlugin extends JavaPlugin {

    private static EdToolsPerksPlugin instance;

    // Managers
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PlayerDataManager playerDataManager;
    private PerkManager perkManager;
    private BoosterManager boosterManager;
    private GuiManager guiManager;
    private EdToolsIntegration edToolsIntegration;

    // Listeners
    private PlayerConnectionListener playerConnectionListener;
    private InventoryClickListener inventoryClickListener;
    private ToolInteractListener toolInteractListener;
    private PerkEffectListener perkEffectListener;

    // PlaceholderAPI
    private PerksPlaceholderExpansion placeholderExpansion;
    private boolean placeholderApiEnabled = false;

    @Override
    public void onEnable() {
        instance = this;
        long startTime = System.currentTimeMillis();

        getLogger().info("========================================");
        getLogger().info("  OkiMC-EdToolsPerks v" + getDescription().getVersion());
        getLogger().info("  Author: Snopeyy");
        getLogger().info("========================================");

        // Initialize in order of dependencies
        if (!initializeConfigs()) {
            getLogger().severe("Failed to initialize configurations! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!initializeDatabase()) {
            getLogger().severe("Failed to initialize database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!initializeIntegrations()) {
            getLogger().severe("Failed to initialize EdTools integration! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        initializeManagers();
        initializeListeners();
        registerCommands();
        initializePlaceholderAPI();

        // Load data for online players (in case of reload)
        loadOnlinePlayers();

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("EdToolsPerks enabled successfully! (" + loadTime + "ms)");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling EdToolsPerks...");

        // Close all GUIs
        if (guiManager != null) {
            guiManager.closeAll();
        }

        // Clear inventory listener maps
        if (inventoryClickListener != null) {
            inventoryClickListener.cleanup();
        }

        // Remove all boosters
        if (boosterManager != null) {
            boosterManager.clearAll();
        }

        // Save all player data with timeout on disable
        if (playerDataManager != null) {
            try {
                // Give 5 seconds for async save, then force shutdown
                playerDataManager.shutdown().orTimeout(5, TimeUnit.SECONDS).join();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Timeout waiting for player data save", e);
            }
        }

        // Close database connections
        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        // Clear skull cache
        SkullUtils.clearCache();

        // Clear static references
        instance = null;

        getLogger().info("EdToolsPerks disabled.");
    }

    /**
     * Initializes all configuration files.
     *
     * @return true if successful
     */
    private boolean initializeConfigs() {
        try {
            configManager = new ConfigManager(this);
            return configManager.loadAll();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to load configurations!", e);
            return false;
        }
    }

    /**
     * Initializes the database connection and tables.
     *
     * @return true if successful
     */
    private boolean initializeDatabase() {
        try {
            databaseManager = new DatabaseManager(this, configManager);
            // Block and wait for async initialization to complete (with 30s timeout)
            return databaseManager.initialize().orTimeout(30, TimeUnit.SECONDS).get();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database!", e);
            return false;
        }
    }

    /**
     * Initializes EdTools integration.
     *
     * @return true if successful
     */
    private boolean initializeIntegrations() {
        // Check if EdTools is present
        if (getServer().getPluginManager().getPlugin("EdTools") == null) {
            getLogger().severe("EdTools plugin not found! This plugin requires EdTools to function.");
            return false;
        }

        try {
            edToolsIntegration = new EdToolsIntegration(this, configManager);
            // Initialize the EdTools API connections
            if (!edToolsIntegration.initialize()) {
                getLogger().severe("Failed to initialize EdTools API!");
                return false;
            }
            if (!edToolsIntegration.isInitialized()) {
                getLogger().warning("EdTools API not fully available. Some features may not work.");
            }
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize EdTools integration!", e);
            return false;
        }
    }

    /**
     * Initializes all manager classes.
     */
    private void initializeManagers() {
        // Player data manager
        playerDataManager = new PlayerDataManager(this, configManager, databaseManager);

        // Booster manager
        boosterManager = new BoosterManager(this, configManager, edToolsIntegration);

        // Perk manager
        perkManager = new PerkManager(this, configManager, playerDataManager, boosterManager);

        // GUI manager
        guiManager = new GuiManager(this, configManager, playerDataManager,
            perkManager, boosterManager, edToolsIntegration);

        if (configManager.isDebug()) {
            getLogger().info("[DEBUG] All managers initialized.");
        }
    }

    /**
     * Initializes and registers all event listeners.
     */
    private void initializeListeners() {
        // Inventory click listener (needed by other listeners)
        inventoryClickListener = new InventoryClickListener(this, configManager, guiManager,
            playerDataManager, perkManager, boosterManager, edToolsIntegration);

        // Link InventoryClickListener to GuiManager for tool type tracking
        guiManager.setInventoryClickListener(inventoryClickListener);

        // Player connection listener
        playerConnectionListener = new PlayerConnectionListener(this, configManager,
            playerDataManager, boosterManager);

        // Tool interaction listener
        toolInteractListener = new ToolInteractListener(this, configManager, edToolsIntegration,
            guiManager, playerDataManager, inventoryClickListener);

        // Perk effect listener (applies boosts on block break)
        perkEffectListener = new PerkEffectListener(this, configManager, edToolsIntegration,
            playerDataManager, boosterManager);

        // Register all listeners
        getServer().getPluginManager().registerEvents(playerConnectionListener, this);
        getServer().getPluginManager().registerEvents(inventoryClickListener, this);
        getServer().getPluginManager().registerEvents(toolInteractListener, this);
        getServer().getPluginManager().registerEvents(perkEffectListener, this);

        if (configManager.isDebug()) {
            getLogger().info("[DEBUG] All listeners registered.");
        }
    }

    /**
     * Registers plugin commands.
     */
    private void registerCommands() {
        PluginCommand perksCommand = getCommand("perks");
        if (perksCommand != null) {
            PerksCommand executor = new PerksCommand(this, configManager, playerDataManager,
                perkManager, boosterManager, guiManager, edToolsIntegration, inventoryClickListener);
            PerksTabCompleter tabCompleter = new PerksTabCompleter(configManager);

            perksCommand.setExecutor(executor);
            perksCommand.setTabCompleter(tabCompleter);

            if (configManager.isDebug()) {
                getLogger().info("[DEBUG] Commands registered.");
            }
        } else {
            getLogger().warning("Failed to register /perks command! Check plugin.yml");
        }
    }

    /**
     * Initializes PlaceholderAPI integration if available.
     */
    private void initializePlaceholderAPI() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new PerksPlaceholderExpansion(this, configManager, playerDataManager);
            placeholderExpansion.register();
            placeholderApiEnabled = true;
            getLogger().info("PlaceholderAPI integration enabled.");
        } else {
            getLogger().info("PlaceholderAPI not found. Placeholders will not be available.");
        }
    }

    /**
     * Loads data for all online players (used during plugin reload).
     */
    private void loadOnlinePlayers() {
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            getLogger().info("Loading data for " + Bukkit.getOnlinePlayers().size() + " online players...");
            playerConnectionListener.loadAllOnlinePlayers();
        }
    }

    // ==================== Static Access ====================

    /**
     * Gets the plugin instance.
     *
     * @return The plugin instance
     */
    public static EdToolsPerksPlugin getInstance() {
        return instance;
    }

    // ==================== Manager Getters ====================

    /**
     * Gets the configuration manager.
     *
     * @return The ConfigManager
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }

    /**
     * Gets the database manager.
     *
     * @return The DatabaseManager
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Gets the player data manager.
     *
     * @return The PlayerDataManager
     */
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    /**
     * Gets the perk manager.
     *
     * @return The PerkManager
     */
    public PerkManager getPerkManager() {
        return perkManager;
    }

    /**
     * Gets the booster manager.
     *
     * @return The BoosterManager
     */
    public BoosterManager getBoosterManager() {
        return boosterManager;
    }

    /**
     * Gets the GUI manager.
     *
     * @return The GuiManager
     */
    public GuiManager getGuiManager() {
        return guiManager;
    }

    /**
     * Gets the EdTools integration.
     *
     * @return The EdToolsIntegration
     */
    public EdToolsIntegration getEdToolsIntegration() {
        return edToolsIntegration;
    }

    /**
     * Checks if PlaceholderAPI is available.
     *
     * @return true if PlaceholderAPI is enabled
     */
    public boolean isPlaceholderApiEnabled() {
        return placeholderApiEnabled;
    }
}
