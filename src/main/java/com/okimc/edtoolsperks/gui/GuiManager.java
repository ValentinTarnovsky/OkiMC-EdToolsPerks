package com.okimc.edtoolsperks.gui;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.GuiConfig;
import com.okimc.edtoolsperks.integration.EdToolsIntegration;
import com.okimc.edtoolsperks.managers.BoosterManager;
import com.okimc.edtoolsperks.managers.PerkManager;
import com.okimc.edtoolsperks.managers.PlayerDataManager;
import com.okimc.edtoolsperks.listeners.InventoryClickListener;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all plugin GUIs.
 * Handles GUI creation, tracking, and action routing.
 *
 * <p>Manages three GUI types:</p>
 * <ul>
 *   <li>Main GUI - Primary perks menu</li>
 *   <li>Perks List GUI - Catalog of available perks</li>
 *   <li>Animation GUI - Roll animation display</li>
 * </ul>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class GuiManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final GuiConfig guiConfig;
    private final PlayerDataManager playerDataManager;
    private final PerkManager perkManager;
    private final BoosterManager boosterManager;
    private final EdToolsIntegration edToolsIntegration;

    // Reference to InventoryClickListener for tracking tool types
    private InventoryClickListener inventoryClickListener;

    // Track open GUIs: player UUID -> GuiType
    private final Map<UUID, GuiType> openGuis = new ConcurrentHashMap<>();

    // Track players in animation (locked from interaction)
    private final Set<UUID> animatingPlayers = ConcurrentHashMap.newKeySet();

    // GUI title identifiers (used for click detection)
    private String mainGuiTitle;
    private String perksListGuiTitle;
    private String animationGuiTitle;

    /**
     * GUI types for tracking purposes.
     */
    public enum GuiType {
        MAIN,
        PERKS_LIST,
        ANIMATION
    }

    /**
     * Creates a new GuiManager instance.
     *
     * @param plugin             The main plugin instance
     * @param configManager      The configuration manager
     * @param playerDataManager  The player data manager
     * @param perkManager        The perk manager
     * @param boosterManager     The booster manager
     * @param edToolsIntegration The EdTools integration
     */
    public GuiManager(JavaPlugin plugin, ConfigManager configManager,
                      PlayerDataManager playerDataManager, PerkManager perkManager,
                      BoosterManager boosterManager, EdToolsIntegration edToolsIntegration) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.guiConfig = configManager.getGuiConfig();
        this.playerDataManager = playerDataManager;
        this.perkManager = perkManager;
        this.boosterManager = boosterManager;
        this.edToolsIntegration = edToolsIntegration;

        loadTitles();
    }

    /**
     * Loads GUI titles from config.
     * Titles are stored as base patterns (placeholders like {tool_name} are stripped for matching).
     * Colors are translated so they match the actual inventory titles.
     */
    private void loadTitles() {
        // Extract base title patterns for flexible matching
        // Remove placeholders like {tool_name} since they're replaced when opening GUI
        // Translate colors so patterns match actual inventory titles
        this.mainGuiTitle = extractTitlePattern(guiConfig.getMainGui().title());
        this.perksListGuiTitle = extractTitlePattern(guiConfig.getPerksListGui().title());
        this.animationGuiTitle = extractTitlePattern(guiConfig.getAnimationGui().title());
    }

    /**
     * Extracts a base title pattern by removing placeholders and translating colors.
     * Used for title matching since actual titles have placeholders replaced and colors translated.
     */
    private String extractTitlePattern(String title) {
        if (title == null) return "";
        // Remove placeholder patterns {xxx} and trim
        String pattern = title.replaceAll("\\{[^}]+}", "").trim();
        // Translate colors so the pattern matches actual inventory titles
        return configManager.getMessagesConfig().translateColors(pattern);
    }

    /**
     * Reloads GUI configuration.
     */
    public void reload() {
        loadTitles();
    }

    /**
     * Sets the InventoryClickListener reference.
     * Called after both GuiManager and InventoryClickListener are created.
     *
     * @param listener The InventoryClickListener
     */
    public void setInventoryClickListener(InventoryClickListener listener) {
        this.inventoryClickListener = listener;
    }

    // ==================== GUI Opening ====================

    /**
     * Opens the main GUI for a player.
     *
     * @param player   The player
     * @param toolType The tool type being used
     */
    public void openMainGui(Player player, String toolType) {
        if (isAnimating(player)) {
            return; // Don't allow opening during animation
        }

        // Track tool type for click handling
        if (inventoryClickListener != null) {
            inventoryClickListener.setPlayerToolType(player, toolType);
        }

        MainGui mainGui = new MainGui(plugin, configManager, playerDataManager,
            perkManager, boosterManager, edToolsIntegration, this);
        mainGui.open(player, toolType);

        openGuis.put(player.getUniqueId(), GuiType.MAIN);
    }

    /**
     * Opens the perks list GUI for a player.
     *
     * @param player   The player
     * @param toolType The tool type to show perks for
     */
    public void openPerksListGui(Player player, String toolType) {
        if (isAnimating(player)) {
            return;
        }

        // Track tool type for click handling (needed for back button)
        if (inventoryClickListener != null) {
            inventoryClickListener.setPlayerToolType(player, toolType);
        }

        PerksListGui perksListGui = new PerksListGui(plugin, configManager,
            playerDataManager, perkManager, boosterManager, this);
        perksListGui.open(player, toolType);

        openGuis.put(player.getUniqueId(), GuiType.PERKS_LIST);
    }

    /**
     * Opens the animation GUI and starts the roll animation.
     *
     * @param player   The player
     * @param toolType The tool type for the roll
     */
    public void openAnimationGui(Player player, String toolType) {
        // Mark player as animating
        animatingPlayers.add(player.getUniqueId());

        // Track tool type for returning to main GUI after animation
        if (inventoryClickListener != null) {
            inventoryClickListener.setPlayerToolType(player, toolType);
        }

        AnimationGui animationGui = new AnimationGui(plugin, configManager,
            playerDataManager, perkManager, boosterManager, this);
        animationGui.open(player, toolType);

        openGuis.put(player.getUniqueId(), GuiType.ANIMATION);
    }

    // ==================== GUI State Management ====================

    /**
     * Called when a player closes a GUI.
     *
     * @param player The player
     */
    public void handleClose(Player player) {
        UUID uuid = player.getUniqueId();
        openGuis.remove(uuid);
        // Note: animatingPlayers is cleared by AnimationGui when complete
    }

    /**
     * Marks a player as no longer animating.
     *
     * @param player The player
     */
    public void clearAnimating(Player player) {
        animatingPlayers.remove(player.getUniqueId());
    }

    /**
     * Checks if a player is currently in an animation.
     *
     * @param player The player
     * @return true if animating
     */
    public boolean isAnimating(Player player) {
        return animatingPlayers.contains(player.getUniqueId());
    }

    /**
     * Checks if an inventory belongs to this plugin's GUIs.
     *
     * @param inventory The inventory to check
     * @return true if it's a plugin GUI
     */
    public boolean isPluginGui(Inventory inventory) {
        if (inventory == null || inventory.getViewers().isEmpty()) {
            return false;
        }

        String title = inventory.getViewers().get(0).getOpenInventory().getTitle();
        return isPluginGuiTitle(title);
    }

    /**
     * Checks if a title matches any plugin GUI.
     * Uses contains matching since titles have placeholders replaced.
     *
     * @param title The inventory title
     * @return true if it's a plugin GUI title
     */
    public boolean isPluginGuiTitle(String title) {
        if (title == null) return false;
        // Use contains since actual titles have placeholders replaced
        // Order matters: check more specific patterns first, then generic ones
        return title.contains(perksListGuiTitle) ||
               title.contains(animationGuiTitle) ||
               title.contains(mainGuiTitle);
    }

    /**
     * Gets the GUI type for an inventory title.
     * Uses contains matching since titles have placeholders replaced.
     *
     * IMPORTANT: Check order matters! More specific patterns must be checked FIRST.
     * - perksListGuiTitle = "Perks - Lista de Perks" (specific)
     * - animationGuiTitle = "Menu | Mejoras" (specific)
     * - mainGuiTitle = "Perks -" (generic, substring of perksListGuiTitle)
     *
     * If MAIN is checked first, Perks List GUI would be misidentified as MAIN
     * because "Perks - Lista de Perks".contains("Perks -") == true.
     *
     * @param title The inventory title
     * @return The GuiType or null
     */
    public GuiType getGuiType(String title) {
        if (title == null) return null;
        // Check MOST SPECIFIC patterns first, LEAST SPECIFIC (generic) last
        if (title.contains(perksListGuiTitle)) return GuiType.PERKS_LIST;
        if (title.contains(animationGuiTitle)) return GuiType.ANIMATION;
        if (title.contains(mainGuiTitle)) return GuiType.MAIN;
        return null;
    }

    /**
     * Gets the open GUI type for a player.
     *
     * @param player The player
     * @return The GuiType or null if no GUI open
     */
    public GuiType getOpenGuiType(Player player) {
        return openGuis.get(player.getUniqueId());
    }

    /**
     * Checks if a player has a GUI open.
     *
     * @param player The player
     * @return true if a GUI is open
     */
    public boolean hasGuiOpen(Player player) {
        return openGuis.containsKey(player.getUniqueId());
    }

    // ==================== Cleanup ====================

    /**
     * Closes all open GUIs for all players.
     */
    public void closeAll() {
        for (UUID uuid : openGuis.keySet()) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        }
        openGuis.clear();
        animatingPlayers.clear();
    }

    /**
     * Closes the GUI for a specific player.
     *
     * @param player The player
     */
    public void closeGui(Player player) {
        if (hasGuiOpen(player)) {
            player.closeInventory();
        }
    }

    // ==================== Getters ====================

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GuiConfig getGuiConfig() {
        return guiConfig;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public PerkManager getPerkManager() {
        return perkManager;
    }

    public BoosterManager getBoosterManager() {
        return boosterManager;
    }

    public EdToolsIntegration getEdToolsIntegration() {
        return edToolsIntegration;
    }

    public String getMainGuiTitle() {
        return mainGuiTitle;
    }

    public String getPerksListGuiTitle() {
        return perksListGuiTitle;
    }

    public String getAnimationGuiTitle() {
        return animationGuiTitle;
    }
}
