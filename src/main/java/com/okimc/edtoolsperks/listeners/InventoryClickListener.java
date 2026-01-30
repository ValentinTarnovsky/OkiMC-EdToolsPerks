package com.okimc.edtoolsperks.listeners;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.GuiConfig.GuiDefinition;
import com.okimc.edtoolsperks.config.GuiConfig.GuiItem;
import com.okimc.edtoolsperks.config.MessagesConfig;
import com.okimc.edtoolsperks.gui.GuiManager;
import com.okimc.edtoolsperks.gui.GuiManager.GuiType;
import com.okimc.edtoolsperks.integration.EdToolsIntegration;
import com.okimc.edtoolsperks.managers.BoosterManager;
import com.okimc.edtoolsperks.managers.PerkManager;
import com.okimc.edtoolsperks.managers.PlayerDataManager;
import com.okimc.edtoolsperks.model.ActivePerk;
import com.okimc.edtoolsperks.model.PlayerData;
import com.okimc.edtoolsperks.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles inventory click events for plugin GUIs.
 *
 * <p>Routes click events to the appropriate GUI handler based on
 * the inventory title and GUI type.</p>
 *
 * <p>Manages:</p>
 * <ul>
 *   <li>Main GUI clicks and actions</li>
 *   <li>Perks List GUI navigation</li>
 *   <li>Animation GUI protection (no clicks during animation)</li>
 *   <li>Inventory close events for cleanup</li>
 * </ul>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class InventoryClickListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final GuiManager guiManager;
    private final PlayerDataManager playerDataManager;
    private final PerkManager perkManager;
    private final BoosterManager boosterManager;
    private final EdToolsIntegration edToolsIntegration;
    private final MessagesConfig messagesConfig;

    // Track tool type per player for GUI context
    private final Map<UUID, String> playerToolTypes = new ConcurrentHashMap<>();

    // Click cooldown to prevent spam (ms)
    private static final long CLICK_COOLDOWN = 200L;
    private final Map<UUID, Long> lastClickTimes = new ConcurrentHashMap<>();

    /**
     * Creates a new InventoryClickListener.
     *
     * @param plugin             The main plugin instance
     * @param configManager      The configuration manager
     * @param guiManager         The GUI manager
     * @param playerDataManager  The player data manager
     * @param perkManager        The perk manager
     * @param boosterManager     The booster manager
     * @param edToolsIntegration The EdTools integration
     */
    public InventoryClickListener(JavaPlugin plugin, ConfigManager configManager,
                                   GuiManager guiManager, PlayerDataManager playerDataManager,
                                   PerkManager perkManager, BoosterManager boosterManager,
                                   EdToolsIntegration edToolsIntegration) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.guiManager = guiManager;
        this.playerDataManager = playerDataManager;
        this.perkManager = perkManager;
        this.boosterManager = boosterManager;
        this.edToolsIntegration = edToolsIntegration;
        this.messagesConfig = configManager.getMessagesConfig();
    }

    /**
     * Handles inventory click events.
     *
     * @param event The click event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        String title = event.getView().getTitle();

        // Check if this is a plugin GUI
        if (!guiManager.isPluginGuiTitle(title)) {
            return;
        }

        // Always cancel clicks in plugin GUIs
        event.setCancelled(true);

        // Check if player is animating (no interactions allowed)
        if (guiManager.isAnimating(player)) {
            return;
        }

        // Click cooldown check
        if (isOnCooldown(player)) {
            return;
        }

        // Clicked outside the GUI
        if (event.getClickedInventory() == null || event.getClickedInventory() != inventory) {
            return;
        }

        int slot = event.getRawSlot();
        ItemStack clickedItem = event.getCurrentItem();

        // Determine GUI type and handle
        GuiType guiType = guiManager.getGuiType(title);
        if (guiType == null) {
            return;
        }

        // Play click sound
        playClickSound(player);

        // Route to appropriate handler
        switch (guiType) {
            case MAIN -> handleMainGuiClick(player, slot, clickedItem);
            case PERKS_LIST -> handlePerksListClick(player, slot, clickedItem);
            case ANIMATION -> {
                // No interactions during animation
            }
        }
    }

    /**
     * Handles inventory drag events.
     * Prevents dragging items in plugin GUIs.
     *
     * @param event The drag event
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (guiManager.isPluginGuiTitle(title)) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles inventory close events.
     *
     * @param event The close event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (!guiManager.isPluginGuiTitle(title)) {
            return;
        }

        // Notify GUI manager
        guiManager.handleClose(player);

        // Clean up player tool type tracking
        // Note: Keep it for a tick in case GUI is being switched
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!guiManager.hasGuiOpen(player)) {
                playerToolTypes.remove(player.getUniqueId());
            }
        }, 1L);
    }

    // ==================== Main GUI Handler ====================

    /**
     * Handles clicks in the main GUI.
     */
    private void handleMainGuiClick(Player player, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        PlayerData playerData = playerDataManager.getData(player.getUniqueId());
        if (playerData == null) {
            return;
        }

        String toolType = getPlayerToolType(player);
        GuiDefinition guiDef = configManager.getGuiConfig().getMainGui();

        // Find which item was clicked
        for (GuiItem guiItem : guiDef.items().values()) {
            if (guiItem.slots().contains(slot)) {
                List<String> actions = guiItem.getActions("click");
                for (String action : actions) {
                    executeAction(player, playerData, action, toolType);
                }
                return;
            }
        }
    }

    /**
     * Handles clicks in the perks list GUI.
     */
    private void handlePerksListClick(Player player, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        String toolType = getPlayerToolType(player);
        GuiDefinition guiDef = configManager.getGuiConfig().getPerksListGui();

        // Find which item was clicked
        for (GuiItem guiItem : guiDef.items().values()) {
            if (guiItem.slots().contains(slot)) {
                List<String> actions = guiItem.getActions("click");
                for (String action : actions) {
                    executePerksListAction(player, action, toolType);
                }
                return;
            }
        }

        // Perk item click - just informational, no action
    }

    // ==================== Action Execution ====================

    /**
     * Executes a GUI action for the main GUI.
     */
    private void executeAction(Player player, PlayerData data, String action, String toolType) {
        if (action == null || action.equalsIgnoreCase("none")) {
            return;
        }

        String actionLower = action.toLowerCase();

        // Roll action: roll:N
        if (actionLower.startsWith("roll:")) {
            int amount = parseIntOrDefault(action.substring(5), 1);
            handleRoll(player, data, toolType, amount);
            return;
        }

        // Open GUI action: open-gui:name
        if (actionLower.startsWith("open-gui:")) {
            String guiName = action.substring(9).trim();
            handleOpenGui(player, guiName, toolType);
            return;
        }

        // Buy rolls action: buy-rolls:amount:cost
        if (actionLower.startsWith("buy-rolls:")) {
            String[] parts = action.substring(10).split(":");
            if (parts.length >= 2) {
                int amount = parseIntOrDefault(parts[0], 1);
                double cost = parseDoubleOrDefault(parts[1], 0);
                handleBuyRolls(player, data, amount, cost);
            }
            return;
        }

        // Toggle animations
        if (actionLower.equals("toggle-anim") || actionLower.equals("toggle-animations")) {
            handleToggleAnimations(player, data, toolType);
            return;
        }

        // Close
        if (actionLower.equals("close")) {
            player.closeInventory();
            return;
        }

        // Command execution
        if (actionLower.startsWith("[player]")) {
            String cmd = action.substring(8).trim()
                .replace("{player}", player.getName())
                .replace("{tool}", toolType);
            player.performCommand(cmd);
            return;
        }

        if (actionLower.startsWith("[console]")) {
            String cmd = action.substring(9).trim()
                .replace("{player}", player.getName())
                .replace("{tool}", toolType);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    /**
     * Executes a GUI action for the perks list GUI.
     */
    private void executePerksListAction(Player player, String action, String toolType) {
        if (action == null || action.equalsIgnoreCase("none")) {
            return;
        }

        String actionLower = action.toLowerCase();

        // Back to main GUI
        if (actionLower.equals("back") || actionLower.equals("open-gui:main")) {
            guiManager.openMainGui(player, toolType);
            return;
        }

        // Open another GUI
        if (actionLower.startsWith("open-gui:")) {
            String guiName = action.substring(9).trim();
            handleOpenGui(player, guiName, toolType);
            return;
        }

        // Close
        if (actionLower.equals("close")) {
            player.closeInventory();
        }
    }

    // ==================== Action Handlers ====================

    /**
     * Handles the roll action.
     */
    private void handleRoll(Player player, PlayerData data, String toolType, int amount) {
        // Check if player has enough rolls
        if (!data.hasRolls(amount)) {
            messagesConfig.send(player, "errors.not-enough-rolls",
                "required", String.valueOf(amount),
                "current", String.valueOf(data.getRolls()));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Check if animations are enabled
        if (data.isAnimationsEnabled()) {
            // Close inventory and open animation GUI
            player.closeInventory();
            guiManager.openAnimationGui(player, toolType);
        } else {
            // Direct roll without animation - perform all rolls sequentially
            // Don't close the GUI, we'll refresh it after rolling
            executeMultipleRolls(player, toolType, amount, 0);
        }
    }

    /**
     * Executes multiple rolls sequentially without animation.
     *
     * @param player   The player
     * @param toolType The tool type
     * @param total    Total number of rolls to perform
     * @param current  Current roll index (0-based)
     */
    private void executeMultipleRolls(Player player, String toolType, int total, int current) {
        if (current >= total) {
            // All rolls completed - only refresh GUI if player still has one open
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && player.getOpenInventory().getTopInventory().getSize() > 5) {
                    guiManager.openMainGui(player, toolType);
                }
            });
            return;
        }

        perkManager.roll(player, toolType).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.isSuccess()) {
                    ActivePerk perk = result.getPerk();
                    String categoryColor = messagesConfig.getCategoryColor(perk.getCategory());

                    messagesConfig.send(player, "rolls.success",
                        "perk", categoryColor + perk.getDisplayName(),
                        "level", String.valueOf(perk.getLevel()),
                        "tool", formatToolName(toolType));

                    if (result.wasPityTriggered()) {
                        messagesConfig.send(player, "rolls.pity-triggered");
                    }

                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                } else {
                    messagesConfig.send(player, "errors.roll-failed");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }

                // Execute next roll after a small delay to prevent spam
                if (current + 1 < total) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        executeMultipleRolls(player, toolType, total, current + 1);
                    }, 5L); // 5 tick delay between rolls (~250ms)
                } else {
                    // Last roll completed - only refresh GUI if player still has one open
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && player.getOpenInventory().getTopInventory().getSize() > 5) {
                            guiManager.openMainGui(player, toolType);
                        }
                    }, 10L);
                }
            });
        });
    }

    /**
     * Handles opening another GUI.
     */
    private void handleOpenGui(Player player, String guiName, String toolType) {
        switch (guiName.toLowerCase()) {
            case "perks-list", "perks", "list" -> guiManager.openPerksListGui(player, toolType);
            case "main", "main-gui" -> guiManager.openMainGui(player, toolType);
            default -> {
                if (configManager.isDebug()) {
                    plugin.getLogger().warning("Unknown GUI: " + guiName);
                }
            }
        }
    }

    /**
     * Handles buying rolls with currency.
     */
    private void handleBuyRolls(Player player, PlayerData data, int amount, double cost) {
        // Debug logging to help diagnose currency issues
        if (configManager.isDebug()) {
            String currency = edToolsIntegration.getRollCurrency();
            double balance = edToolsIntegration.getBalance(player);
            plugin.getLogger().info("[DEBUG] handleBuyRolls: player=" + player.getName() +
                ", amount=" + amount + ", cost=" + cost +
                ", currency=" + currency + ", balance=" + balance +
                ", hasEnough=" + (balance >= cost));
        }

        if (cost > 0 && !edToolsIntegration.hasBalance(player, cost)) {
            messagesConfig.send(player, "errors.not-enough-currency",
                "required", MessageUtils.formatNumber(cost),
                "current", MessageUtils.formatNumber(edToolsIntegration.getBalance(player)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Withdraw currency
        if (cost > 0) {
            if (!edToolsIntegration.withdraw(player, cost)) {
                messagesConfig.send(player, "errors.transaction-failed");
                return;
            }
        }

        // Add rolls (playerDataManager.addRolls already updates the cache)
        playerDataManager.addRolls(player.getUniqueId(), amount);

        messagesConfig.send(player, "rolls.purchased",
            "amount", String.valueOf(amount),
            "cost", MessageUtils.formatNumber(cost),
            "total", String.valueOf(data.getRolls()));

        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        // Refresh GUI
        String toolType = getPlayerToolType(player);
        guiManager.openMainGui(player, toolType);
    }

    /**
     * Handles toggling animations.
     */
    private void handleToggleAnimations(Player player, PlayerData data, String toolType) {
        playerDataManager.toggleAnimations(player.getUniqueId()).thenAccept(newState -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (newState) {
                    messagesConfig.send(player, "animations.enabled");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                } else {
                    messagesConfig.send(player, "animations.disabled");
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 0.5f);
                }
                // Refresh GUI
                guiManager.openMainGui(player, toolType);
            });
        });
    }

    // ==================== Utility Methods ====================

    /**
     * Sets the tool type for a player (called when opening GUI).
     */
    public void setPlayerToolType(Player player, String toolType) {
        playerToolTypes.put(player.getUniqueId(), toolType);
    }

    /**
     * Gets the current tool type for a player.
     */
    public String getPlayerToolType(Player player) {
        return playerToolTypes.getOrDefault(player.getUniqueId(), "unknown");
    }

    /**
     * Checks if a player is on click cooldown.
     */
    private boolean isOnCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long lastClick = lastClickTimes.get(player.getUniqueId());

        if (lastClick != null && (now - lastClick) < CLICK_COOLDOWN) {
            return true;
        }

        lastClickTimes.put(player.getUniqueId(), now);
        return false;
    }

    /**
     * Plays a click sound to the player.
     */
    private void playClickSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * Formats a tool type for display.
     */
    private String formatToolName(String toolType) {
        if (toolType == null) return messagesConfig.get("fallback.unknown-tool", "Unknown");
        return configManager.getToolDisplayName(toolType);
    }

    private int parseIntOrDefault(String str, int def) {
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private double parseDoubleOrDefault(String str, double def) {
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        playerToolTypes.clear();
        lastClickTimes.clear();
    }
}
