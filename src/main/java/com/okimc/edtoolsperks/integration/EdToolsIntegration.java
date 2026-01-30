package com.okimc.edtoolsperks.integration;

import com.okimc.edtoolsperks.config.ConfigManager;
import es.edwardbelt.edgens.iapi.EdToolsAPI;
import es.edwardbelt.edgens.iapi.EdToolsBoostersAPI;
import es.edwardbelt.edgens.iapi.EdToolsCurrencyAPI;
import es.edwardbelt.edgens.iapi.EdToolsOmniToolAPI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Integration layer for EdTools API.
 * Handles OmniTool detection, currency operations, and tool type identification.
 *
 * <p>This class provides a safe wrapper around EdTools APIs with proper
 * null checks and error handling.</p>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class EdToolsIntegration {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;

    private EdToolsAPI edToolsAPI;
    private EdToolsOmniToolAPI omniToolAPI;
    private EdToolsCurrencyAPI currencyAPI;
    private EdToolsBoostersAPI boostersAPI;

    private boolean initialized;

    /**
     * Creates a new EdToolsIntegration instance.
     *
     * @param plugin        The main plugin instance
     * @param configManager The configuration manager
     */
    public EdToolsIntegration(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.initialized = false;
    }

    /**
     * Initializes the EdTools API connections.
     *
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            edToolsAPI = EdToolsAPI.getInstance();

            if (edToolsAPI == null) {
                plugin.getLogger().severe("EdTools API not available! Is EdTools installed?");
                return false;
            }

            omniToolAPI = edToolsAPI.getOmniToolAPI();
            if (omniToolAPI == null) {
                plugin.getLogger().severe("EdTools OmniTool API not available!");
                return false;
            }

            currencyAPI = edToolsAPI.getCurrencyAPI();
            if (currencyAPI == null) {
                plugin.getLogger().warning("EdTools Currency API not available. Currency features disabled.");
            }

            // Try to get Boosters API (may not exist in older EdTools versions)
            try {
                boostersAPI = edToolsAPI.getBoostersAPI();
                if (boostersAPI == null) {
                    plugin.getLogger().warning("EdTools Boosters API not available. Boosters will be tracked locally only.");
                }
            } catch (NoSuchMethodError | Exception e) {
                plugin.getLogger().warning("EdTools Boosters API not available (method not found). Boosters will be tracked locally only.");
                boostersAPI = null;
            }

            initialized = true;
            plugin.getLogger().info("EdTools integration initialized successfully.");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize EdTools integration!", e);
            return false;
        }
    }

    // ==================== OmniTool Detection ====================

    /**
     * Checks if an item is an EdTools OmniTool.
     *
     * @param item The item to check
     * @return true if the item is an OmniTool
     */
    public boolean isOmniTool(ItemStack item) {
        if (!initialized || omniToolAPI == null) {
            return false;
        }

        // Null safety check
        if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
            return false;
        }

        try {
            return omniToolAPI.isItemOmniTool(item);
        } catch (Exception e) {
            if (configManager.isDebug()) {
                plugin.getLogger().log(Level.WARNING, "Error checking OmniTool status", e);
            }
            return false;
        }
    }

    /**
     * Checks if a player is holding an OmniTool in their main hand.
     *
     * @param player The player to check
     * @return true if holding an OmniTool
     */
    public boolean isHoldingOmniTool(Player player) {
        if (player == null) return false;
        return isOmniTool(player.getInventory().getItemInMainHand());
    }

    /**
     * Gets the OmniTool from a player's main hand if present.
     *
     * @param player The player
     * @return Optional containing the OmniTool, or empty
     */
    public Optional<ItemStack> getHeldOmniTool(Player player) {
        if (player == null) return Optional.empty();

        ItemStack item = player.getInventory().getItemInMainHand();
        if (isOmniTool(item)) {
            return Optional.of(item);
        }
        return Optional.empty();
    }

    /**
     * Determines the tool type from an OmniTool item.
     * Returns the EdTools OmniTool ID directly without any mapping.
     *
     * @param item The OmniTool item
     * @return The tool type string (the OmniTool ID from EdTools), or "unknown" if not detectable
     */
    public String getToolType(ItemStack item) {
        if (!isOmniTool(item)) {
            return "unknown";
        }

        if (omniToolAPI == null) {
            return "unknown";
        }

        try {
            // Get OmniTool ID directly from EdTools API
            String omniToolId = omniToolAPI.getOmniToolId(item);
            if (omniToolId != null && !omniToolId.isEmpty()) {
                return omniToolId.toLowerCase();
            }
        } catch (NoSuchMethodError e) {
            // Method doesn't exist in this version of EdTools
            if (configManager.isDebug()) {
                plugin.getLogger().info("[DEBUG] getOmniToolId() not available in EdTools API");
            }
        } catch (Exception e) {
            if (configManager.isDebug()) {
                plugin.getLogger().log(Level.WARNING, "Error getting OmniTool ID from API", e);
            }
        }

        return "unknown";
    }

    /**
     * Gets the tool type from the player's held OmniTool.
     *
     * @param player The player
     * @return The tool type or "unknown"
     */
    public String getHeldToolType(Player player) {
        return getHeldOmniTool(player)
            .map(this::getToolType)
            .orElse("unknown");
    }

    // ==================== Currency Operations ====================

    /**
     * Gets the configured currency name for rolls.
     *
     * @return The currency name from config
     */
    public String getRollCurrency() {
        return configManager.getRollCurrency();
    }

    /**
     * Gets a player's balance for the roll currency.
     *
     * @param player The player
     * @return The balance, or 0 if unavailable
     */
    public double getBalance(Player player) {
        return getCurrencyBalance(player, getRollCurrency());
    }

    /**
     * Gets a player's balance for a specific currency.
     *
     * @param player       The player
     * @param currencyName The currency name
     * @return The balance, or 0 if unavailable
     */
    public double getCurrencyBalance(Player player, String currencyName) {
        if (!initialized || currencyAPI == null || player == null) {
            if (configManager.isDebug()) {
                plugin.getLogger().warning("[DEBUG] getCurrencyBalance failed: " +
                    "initialized=" + initialized + ", currencyAPI=" + (currencyAPI != null) +
                    ", player=" + (player != null));
            }
            return 0.0;
        }

        try {
            double balance = currencyAPI.getCurrency(player.getUniqueId(), currencyName);
            if (configManager.isDebug()) {
                plugin.getLogger().info("[DEBUG] getCurrencyBalance: player=" + player.getName() +
                    ", currency=" + currencyName + ", balance=" + balance);
            }
            return balance;
        } catch (Exception e) {
            if (configManager.isDebug()) {
                plugin.getLogger().log(Level.WARNING,
                    "[DEBUG] Error getting currency balance for " + player.getName() +
                    " (currency: " + currencyName + ")", e);
            }
            return 0.0;
        }
    }

    /**
     * Checks if a player has enough of the roll currency.
     *
     * @param player The player
     * @param amount The amount required
     * @return true if player has enough
     */
    public boolean hasBalance(Player player, double amount) {
        return getBalance(player) >= amount;
    }

    /**
     * Checks if a player has enough of a specific currency.
     *
     * @param player       The player
     * @param currencyName The currency name
     * @param amount       The amount required
     * @return true if player has enough
     */
    public boolean hasCurrency(Player player, String currencyName, double amount) {
        return getCurrencyBalance(player, currencyName) >= amount;
    }

    /**
     * Withdraws currency from a player.
     *
     * @param player The player
     * @param amount The amount to withdraw
     * @return true if successful
     */
    public boolean withdraw(Player player, double amount) {
        return withdrawCurrency(player, getRollCurrency(), amount);
    }

    /**
     * Withdraws a specific currency from a player.
     *
     * @param player       The player
     * @param currencyName The currency name
     * @param amount       The amount to withdraw
     * @return true if successful
     */
    public boolean withdrawCurrency(Player player, String currencyName, double amount) {
        if (!initialized || currencyAPI == null || player == null) {
            return false;
        }

        if (!hasCurrency(player, currencyName, amount)) {
            return false;
        }

        try {
            currencyAPI.removeCurrency(player.getUniqueId(), currencyName, amount);
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                "Error withdrawing currency from " + player.getName(), e);
            return false;
        }
    }

    /**
     * Deposits currency to a player.
     *
     * @param player The player
     * @param amount The amount to deposit
     */
    public void deposit(Player player, double amount) {
        depositCurrency(player, getRollCurrency(), amount);
    }

    /**
     * Deposits a specific currency to a player.
     *
     * @param player       The player
     * @param currencyName The currency name
     * @param amount       The amount to deposit
     */
    public void depositCurrency(Player player, String currencyName, double amount) {
        if (!initialized || currencyAPI == null || player == null) {
            return;
        }

        try {
            currencyAPI.addCurrency(player.getUniqueId(), currencyName, amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                "Error depositing currency to " + player.getName(), e);
        }
    }

    /**
     * Deposits currency to a player by UUID (for offline operations).
     *
     * @param uuid         The player's UUID
     * @param currencyName The currency name
     * @param amount       The amount to deposit
     */
    public void depositCurrency(UUID uuid, String currencyName, double amount) {
        if (!initialized || currencyAPI == null || uuid == null) {
            return;
        }

        try {
            currencyAPI.addCurrency(uuid, currencyName, amount);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                "Error depositing currency to " + uuid, e);
        }
    }

    // ==================== Status ====================

    /**
     * Checks if EdTools integration is initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Checks if currency API is available.
     *
     * @return true if currency operations are available
     */
    public boolean isCurrencyAvailable() {
        return initialized && currencyAPI != null;
    }

    /**
     * Gets the raw EdTools API instance.
     *
     * @return The EdToolsAPI or null
     */
    public EdToolsAPI getEdToolsAPI() {
        return edToolsAPI;
    }

    /**
     * Gets the OmniTool API instance.
     *
     * @return The EdToolsOmniToolAPI or null
     */
    public EdToolsOmniToolAPI getOmniToolAPI() {
        return omniToolAPI;
    }

    /**
     * Gets the Currency API instance.
     *
     * @return The EdToolsCurrencyAPI or null
     */
    public EdToolsCurrencyAPI getCurrencyAPI() {
        return currencyAPI;
    }

    /**
     * Gets the Boosters API instance.
     *
     * @return The EdToolsBoostersAPI or null if not available
     */
    public EdToolsBoostersAPI getBoostersAPI() {
        return boostersAPI;
    }

    /**
     * Checks if the Boosters API is available.
     *
     * @return true if Boosters API is available
     */
    public boolean isBoostersAvailable() {
        return initialized && boostersAPI != null;
    }
}
