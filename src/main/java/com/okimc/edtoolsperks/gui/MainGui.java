package com.okimc.edtoolsperks.gui;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.GuiConfig.GuiDefinition;
import com.okimc.edtoolsperks.config.GuiConfig.GuiItem;
import com.okimc.edtoolsperks.config.MessagesConfig;
import com.okimc.edtoolsperks.integration.EdToolsIntegration;
import com.okimc.edtoolsperks.managers.BoosterManager;
import com.okimc.edtoolsperks.managers.PerkManager;
import com.okimc.edtoolsperks.managers.PlayerDataManager;
import com.okimc.edtoolsperks.model.ActivePerk;
import com.okimc.edtoolsperks.model.PlayerData;
import com.okimc.edtoolsperks.utils.ItemBuilder;
import com.okimc.edtoolsperks.utils.MessageUtils;
import com.okimc.edtoolsperks.utils.SkullUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main GUI for the perks system.
 * Displays tool info, current perk, roll button, and navigation options.
 *
 * @author Snopeyy
 * @version 2.0.0
 */
public class MainGui {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final PerkManager perkManager;
    private final BoosterManager boosterManager;
    private final EdToolsIntegration edToolsIntegration;
    private final GuiManager guiManager;
    private final MessagesConfig messagesConfig;

    private String currentToolType;

    /**
     * Creates a new MainGui instance.
     */
    public MainGui(JavaPlugin plugin, ConfigManager configManager,
                   PlayerDataManager playerDataManager, PerkManager perkManager,
                   BoosterManager boosterManager, EdToolsIntegration edToolsIntegration,
                   GuiManager guiManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.perkManager = perkManager;
        this.boosterManager = boosterManager;
        this.edToolsIntegration = edToolsIntegration;
        this.guiManager = guiManager;
        this.messagesConfig = configManager.getMessagesConfig();
    }

    /**
     * Opens the main GUI for a player.
     *
     * @param player   The player
     * @param toolType The tool type
     */
    public void open(Player player, String toolType) {
        this.currentToolType = toolType;

        GuiDefinition guiDef = configManager.getGuiConfig().getMainGui();
        PlayerData playerData = playerDataManager.getData(player.getUniqueId());

        if (playerData == null) {
            messagesConfig.send(player, "errors.data-not-loaded");
            return;
        }

        // Build replacement map for placeholders
        Map<String, String> replacements = buildReplacements(player, playerData, toolType);

        // Apply placeholders to title
        String title = MessageUtils.replace(guiDef.title(), replacements);
        title = messagesConfig.translateColors(title);

        // Create inventory with processed title
        Inventory inventory = Bukkit.createInventory(null, guiDef.size(), title);

        // Populate items from config
        for (GuiItem guiItem : guiDef.items().values()) {
            ItemStack item;

            // Special handling for tool-display - clone the actual held item
            if (guiItem.key().equals("tool-display")) {
                ItemStack heldItem = player.getInventory().getItemInMainHand();
                if (heldItem.getType() != Material.AIR) {
                    item = heldItem.clone();
                } else {
                    item = createItem(guiItem, replacements, playerData, toolType);
                }
            } else {
                item = createItem(guiItem, replacements, playerData, toolType);
            }

            if (item != null) {
                for (int slot : guiItem.slots()) {
                    if (slot >= 0 && slot < inventory.getSize()) {
                        inventory.setItem(slot, item);
                    }
                }
            }
        }

        player.openInventory(inventory);
    }

    /**
     * Builds the replacement map for placeholders.
     */
    private Map<String, String> buildReplacements(Player player, PlayerData data, String toolType) {
        Map<String, String> replacements = new HashMap<>();

        // Player info
        replacements.put("player", player.getName());
        replacements.put("player_name", player.getName());

        // Rolls info
        replacements.put("rolls", String.valueOf(data.getRolls()));
        replacements.put("player_rolls", String.valueOf(data.getRolls()));

        // Pity info
        replacements.put("pity", String.valueOf(data.getPityCount()));
        replacements.put("pity_count", String.valueOf(data.getPityCount()));
        replacements.put("pity_threshold", String.valueOf(configManager.getPityThreshold()));

        // Tool info
        replacements.put("tool_type", toolType);
        replacements.put("tool_name", formatToolName(toolType));

        // Get tool info from player's hand for central display
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        replacements.put("tool_material", heldItem.getType().name());

        // Tool display name and lore for replica
        if (heldItem.hasItemMeta() && heldItem.getItemMeta() != null) {
            var meta = heldItem.getItemMeta();
            String displayName = meta.hasDisplayName() ? meta.getDisplayName() : heldItem.getType().name();
            replacements.put("tool_display_name", displayName);

            if (meta.hasLore() && meta.getLore() != null) {
                replacements.put("tool_display_lore", String.join("\n", meta.getLore()));
            } else {
                replacements.put("tool_display_lore", "");
            }

            if (meta.hasCustomModelData()) {
                replacements.put("tool_model_data", String.valueOf(meta.getCustomModelData()));
            } else {
                replacements.put("tool_model_data", "0");
            }
        } else {
            replacements.put("tool_display_name", formatToolName(toolType));
            replacements.put("tool_display_lore", "");
            replacements.put("tool_model_data", "0");
        }

        // Animation status
        boolean animEnabled = data.isAnimationsEnabled();
        replacements.put("anim_status", animEnabled ?
            messagesConfig.get("gui.animation-enabled") :
            messagesConfig.get("gui.animation-disabled"));
        replacements.put("anim_material", animEnabled ?
            messagesConfig.get("gui.material-anim-enabled", "LIME_DYE") :
            messagesConfig.get("gui.material-anim-disabled", "GRAY_DYE"));

        // Current perk info
        ActivePerk currentPerk = data.getPerk(toolType);
        // Get "no perk" text from config.yml (not messages.yml) - Issue #1 fix
        String noPerkText = configManager.getMainConfig().getString("placeholder-no-perk", "Ninguna");

        if (currentPerk != null && currentPerk.hasDefinition()) {
            String categoryColor = messagesConfig.getCategoryColor(currentPerk.getCategory());
            replacements.put("current_perk", categoryColor + currentPerk.getDisplayName());
            replacements.put("current_perk_name", currentPerk.getDisplayName());
            replacements.put("current_perk_level", String.valueOf(currentPerk.getLevel()));
            replacements.put("current_perk_category", currentPerk.getCategory());

            // Boost info
            var boostMap = currentPerk.getBoostMap();
            StringBuilder boostStr = new StringBuilder();
            for (var entry : boostMap.entrySet()) {
                if (boostStr.length() > 0) boostStr.append(", ");
                String displayName = boosterManager.getBoostDisplayName(entry.getKey());
                boostStr.append("+").append(entry.getValue()).append("% ").append(displayName);
            }
            replacements.put("current_perk_boost", boostStr.toString());
        } else {
            replacements.put("current_perk", noPerkText);
            replacements.put("current_perk_name", noPerkText);
            replacements.put("current_perk_level", "0");
            replacements.put("current_perk_category", "none");
            replacements.put("current_perk_boost", messagesConfig.get("gui.no-boost"));
        }

        // Currency balance
        double balance = edToolsIntegration.getBalance(player);
        replacements.put("balance", MessageUtils.formatNumber(balance));

        return replacements;
    }

    /**
     * Creates an ItemStack from a GuiItem definition.
     */
    private ItemStack createItem(GuiItem guiItem, Map<String, String> replacements,
                                  PlayerData playerData, String toolType) {
        // Apply replacements to the item
        GuiItem processedItem = guiItem.withReplacements(replacements);

        // Handle dynamic materials
        String materialStr = processedItem.getMaterialString();

        // Replace any remaining placeholders in material
        materialStr = MessageUtils.replace(materialStr, replacements);

        ItemStack item;

        // Check for texture
        if (materialStr.toLowerCase().startsWith("texture-")) {
            String texture = materialStr.substring("texture-".length());
            item = SkullUtils.createCustomHead(texture);
        } else {
            // Try to parse material
            try {
                Material material = Material.valueOf(materialStr.toUpperCase());
                item = new ItemStack(material);
            } catch (IllegalArgumentException e) {
                item = new ItemStack(Material.BARRIER);
            }
        }

        // Apply meta
        ItemBuilder builder = new ItemBuilder(item)
            .name(processedItem.name())
            .lore(processedItem.lore())
            .hideAll();  // Issue #3: Hide all vanilla attributes by default

        // Issue #4: Apply glow effect if configured
        if (guiItem.hasGlow()) {
            builder.glow();
        }

        // Issue #4: Apply custom model data if configured
        if (guiItem.hasModelData()) {
            builder.customModelData(guiItem.modelData());
        }

        // Store action data in item
        if (processedItem.hasActions()) {
            builder.data(plugin, "gui_action", guiItem.key());
            builder.data(plugin, "tool_type", toolType);
        }

        return builder.build();
    }

    /**
     * Handles a click action in the main GUI.
     *
     * @param player The player
     * @param slot   The clicked slot
     * @param item   The clicked item
     */
    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        GuiDefinition guiDef = configManager.getGuiConfig().getMainGui();
        PlayerData playerData = playerDataManager.getData(player.getUniqueId());

        if (playerData == null) return;

        // Find which item was clicked
        for (GuiItem guiItem : guiDef.items().values()) {
            if (guiItem.slots().contains(slot)) {
                List<String> actions = guiItem.getActions("click");
                for (String action : actions) {
                    executeAction(player, playerData, action, currentToolType);
                }
                break;
            }
        }
    }

    /**
     * Executes a GUI action.
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
            handleToggleAnimations(player, data);
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
     * Handles the roll action.
     */
    private void handleRoll(Player player, PlayerData data, String toolType, int amount) {
        // Check if player has enough rolls
        if (!data.hasRolls(amount)) {
            messagesConfig.send(player, "errors.not-enough-rolls",
                "required", String.valueOf(amount),
                "current", String.valueOf(data.getRolls()));
            return;
        }

        // Check if animations are enabled
        if (data.isAnimationsEnabled()) {
            // Close inventory and open animation GUI
            player.closeInventory();
            guiManager.openAnimationGui(player, toolType);
        } else {
            // Direct roll without animation - don't close GUI, just refresh after
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
                    } else {
                        messagesConfig.send(player, "errors.roll-failed");
                    }
                    // Only refresh GUI if player still has a chest-type inventory open
                    // (top inventory size > 5 means it's not the crafting grid)
                    if (player.isOnline() && player.getOpenInventory().getTopInventory().getSize() > 5) {
                        guiManager.openMainGui(player, toolType);
                    }
                });
            });
        }
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
        if (cost > 0 && !edToolsIntegration.hasBalance(player, cost)) {
            messagesConfig.send(player, "errors.not-enough-currency",
                "required", MessageUtils.formatNumber(cost),
                "current", MessageUtils.formatNumber(edToolsIntegration.getBalance(player)));
            return;
        }

        // Withdraw currency
        if (cost > 0) {
            if (!edToolsIntegration.withdraw(player, cost)) {
                messagesConfig.send(player, "errors.transaction-failed");
                return;
            }
        }

        // Add rolls (playerDataManager.addRolls already updates the cache, don't call twice!)
        playerDataManager.addRolls(player.getUniqueId(), amount);

        messagesConfig.send(player, "rolls.purchased",
            "amount", String.valueOf(amount),
            "cost", MessageUtils.formatNumber(cost),
            "total", String.valueOf(data.getRolls()));

        // Refresh GUI
        guiManager.openMainGui(player, currentToolType);
    }

    /**
     * Handles toggling animations.
     */
    private void handleToggleAnimations(Player player, PlayerData data) {
        playerDataManager.toggleAnimations(player.getUniqueId()).thenAccept(newState -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (newState) {
                    messagesConfig.send(player, "animations.enabled");
                } else {
                    messagesConfig.send(player, "animations.disabled");
                }
                // Refresh GUI
                guiManager.openMainGui(player, currentToolType);
            });
        });
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

    public String getCurrentToolType() {
        return currentToolType;
    }
}
