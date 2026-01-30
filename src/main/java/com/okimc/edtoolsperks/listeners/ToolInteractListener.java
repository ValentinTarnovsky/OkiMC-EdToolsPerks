package com.okimc.edtoolsperks.listeners;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.MessagesConfig;
import com.okimc.edtoolsperks.gui.GuiManager;
import com.okimc.edtoolsperks.integration.EdToolsIntegration;
import com.okimc.edtoolsperks.managers.PlayerDataManager;
import com.okimc.edtoolsperks.model.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles tool interaction events for EdTools integration.
 *
 * <p>Detects when players interact with OmniTools and opens
 * the perks GUI based on the tool type.</p>
 *
 * <p>Configurable interaction modes:</p>
 * <ul>
 *   <li>SHIFT_RIGHT_CLICK - Sneak + right click (default)</li>
 *   <li>RIGHT_CLICK - Any right click</li>
 *   <li>Command only - No interaction, command only</li>
 * </ul>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class ToolInteractListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final EdToolsIntegration edToolsIntegration;
    private final GuiManager guiManager;
    private final PlayerDataManager playerDataManager;
    private final InventoryClickListener inventoryClickListener;
    private final MessagesConfig messagesConfig;

    // Cooldown to prevent double triggers (ms)
    private static final long INTERACT_COOLDOWN = 500L;
    private final Map<UUID, Long> lastInteractTimes = new ConcurrentHashMap<>();

    /**
     * Creates a new ToolInteractListener.
     *
     * @param plugin                   The main plugin instance
     * @param configManager            The configuration manager
     * @param edToolsIntegration       The EdTools integration
     * @param guiManager               The GUI manager
     * @param playerDataManager        The player data manager
     * @param inventoryClickListener   The inventory click listener (for tool type tracking)
     */
    public ToolInteractListener(JavaPlugin plugin, ConfigManager configManager,
                                 EdToolsIntegration edToolsIntegration, GuiManager guiManager,
                                 PlayerDataManager playerDataManager,
                                 InventoryClickListener inventoryClickListener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.edToolsIntegration = edToolsIntegration;
        this.guiManager = guiManager;
        this.playerDataManager = playerDataManager;
        this.inventoryClickListener = inventoryClickListener;
        this.messagesConfig = configManager.getMessagesConfig();
    }

    /**
     * Handles player interaction events.
     *
     * @param event The interact event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        // Only handle main hand
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // Check if interaction detection is enabled
        if (!configManager.isToolInteractionEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        Action action = event.getAction();

        // Check interaction type based on config
        if (!isValidInteraction(player, action)) {
            return;
        }

        // Get held item
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }

        // Check if it's an OmniTool
        if (!edToolsIntegration.isOmniTool(item)) {
            return;
        }

        // Check cooldown
        if (isOnCooldown(player)) {
            return;
        }

        // Check permission
        if (!player.hasPermission("edtoolsperks.use")) {
            return;
        }

        // Get player data
        PlayerData playerData = playerDataManager.getData(player.getUniqueId());
        if (playerData == null) {
            messagesConfig.send(player, "errors.data-not-loaded");
            return;
        }

        // Determine tool type
        String toolType = edToolsIntegration.getToolType(item);
        if (toolType == null) {
            toolType = "pickaxe"; // Default fallback
        }

        // Cancel the event to prevent normal interaction
        event.setCancelled(true);

        // Store tool type for GUI context
        inventoryClickListener.setPlayerToolType(player, toolType);

        // Open the main GUI
        guiManager.openMainGui(player, toolType);

        if (configManager.isDebug()) {
            plugin.getLogger().info("Player " + player.getName() + " opened perks GUI for " + toolType);
        }
    }

    /**
     * Checks if the interaction is valid based on config settings.
     *
     * @param player The player
     * @param action The action type
     * @return true if the interaction should open the GUI
     */
    private boolean isValidInteraction(Player player, Action action) {
        String interactionMode = configManager.getInteractionMode();

        switch (interactionMode.toLowerCase()) {
            case "shift_right_click" -> {
                return player.isSneaking() &&
                    (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
            }
            case "right_click" -> {
                return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
            }
            case "shift_left_click" -> {
                return player.isSneaking() &&
                    (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
            }
            case "left_click" -> {
                return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
            }
            case "none", "disabled", "command" -> {
                return false; // Only via command
            }
            default -> {
                // Default to shift + right click
                return player.isSneaking() &&
                    (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
            }
        }
    }

    /**
     * Checks if a player is on interaction cooldown.
     *
     * @param player The player
     * @return true if on cooldown
     */
    private boolean isOnCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long lastInteract = lastInteractTimes.get(player.getUniqueId());

        if (lastInteract != null && (now - lastInteract) < INTERACT_COOLDOWN) {
            return true;
        }

        lastInteractTimes.put(player.getUniqueId(), now);
        return false;
    }

    /**
     * Cleans up resources.
     */
    public void cleanup() {
        lastInteractTimes.clear();
    }
}
