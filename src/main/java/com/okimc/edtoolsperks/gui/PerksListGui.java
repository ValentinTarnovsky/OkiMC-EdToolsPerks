package com.okimc.edtoolsperks.gui;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.GuiConfig.GuiDefinition;
import com.okimc.edtoolsperks.config.GuiConfig.GuiItem;
import com.okimc.edtoolsperks.config.MessagesConfig;
import com.okimc.edtoolsperks.config.PerksConfig;
import com.okimc.edtoolsperks.config.PerksConfig.PerkDefinition;
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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * GUI displaying all available perks for a tool type.
 * Shows perks organized by category with current perk highlighted.
 *
 * @author Snopeyy
 * @version 2.0.0
 */
public class PerksListGui {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final PerkManager perkManager;
    private final BoosterManager boosterManager;
    private final GuiManager guiManager;
    private final MessagesConfig messagesConfig;
    private final PerksConfig perksConfig;

    private String currentToolType;

    /**
     * Creates a new PerksListGui instance.
     */
    public PerksListGui(JavaPlugin plugin, ConfigManager configManager,
                        PlayerDataManager playerDataManager, PerkManager perkManager,
                        BoosterManager boosterManager, GuiManager guiManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.perkManager = perkManager;
        this.boosterManager = boosterManager;
        this.guiManager = guiManager;
        this.messagesConfig = configManager.getMessagesConfig();
        this.perksConfig = configManager.getPerksConfig();
    }

    /**
     * Opens the perks list GUI for a player.
     *
     * @param player   The player
     * @param toolType The tool type to show perks for
     */
    public void open(Player player, String toolType) {
        this.currentToolType = toolType;

        GuiDefinition guiDef = configManager.getGuiConfig().getPerksListGui();
        PlayerData playerData = playerDataManager.getData(player.getUniqueId());

        if (playerData == null) {
            messagesConfig.send(player, "errors.data-not-loaded");
            return;
        }

        // Get player's current perk for this tool
        ActivePerk currentPerk = playerData.getPerk(toolType);

        // Build replacement map first for title
        Map<String, String> titleReplacements = new HashMap<>();
        titleReplacements.put("tool_name", formatToolName(toolType));
        titleReplacements.put("tool_type", toolType);
        titleReplacements.put("player", player.getName());

        // Apply placeholders to title
        String title = MessageUtils.replace(guiDef.title(), titleReplacements);
        title = messagesConfig.translateColors(title);

        // Create inventory with processed title
        Inventory inventory = Bukkit.createInventory(null, guiDef.size(), title);
        String currentPerkKey = currentPerk != null ? currentPerk.getPerkKey() : null;

        // Build replacement map
        Map<String, String> replacements = buildReplacements(player, playerData, toolType);

        // Populate static items (borders, back button, etc.)
        for (GuiItem guiItem : guiDef.items().values()) {
            if (!guiItem.key().equals("perk-item")) { // Skip perk template
                ItemStack item = createStaticItem(guiItem, replacements);
                for (int slot : guiItem.slots()) {
                    if (slot >= 0 && slot < inventory.getSize()) {
                        inventory.setItem(slot, item);
                    }
                }
            }
        }

        // Populate perk items
        List<PerkDefinition> sortedPerks = perkManager.getSortedPerksForTool(toolType);
        Set<Integer> perkSlots = guiDef.getPerkSlots();

        // If no specific slots defined, use all empty slots (in order)
        if (perkSlots.isEmpty()) {
            perkSlots = new java.util.LinkedHashSet<>();
            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) {
                    perkSlots.add(i);
                }
            }
        }

        Iterator<Integer> slotIterator = perkSlots.iterator();
        for (PerkDefinition perk : sortedPerks) {
            if (!slotIterator.hasNext()) break;

            int slot = slotIterator.next();
            boolean isEquipped = perk.key().equalsIgnoreCase(currentPerkKey);

            ItemStack perkItem = createPerkItem(perk, isEquipped, currentPerk);
            inventory.setItem(slot, perkItem);
        }

        player.openInventory(inventory);
    }

    /**
     * Builds the replacement map for placeholders.
     */
    private Map<String, String> buildReplacements(Player player, PlayerData data, String toolType) {
        Map<String, String> replacements = new HashMap<>();

        replacements.put("player", player.getName());
        replacements.put("tool_type", toolType);
        replacements.put("tool_name", formatToolName(toolType));
        replacements.put("perk_count", String.valueOf(perksConfig.getPerksForTool(toolType).size()));

        // Current perk info
        ActivePerk currentPerk = data.getPerk(toolType);
        // Get "no perk" text from config.yml (not messages.yml) - Issue #1 fix
        String noPerkText = configManager.getMainConfig().getString("placeholder-no-perk", "Ninguna");

        if (currentPerk != null) {
            String color = messagesConfig.getCategoryColor(currentPerk.getCategory());
            replacements.put("current_perk", color + currentPerk.getDisplayName());
        } else {
            replacements.put("current_perk", noPerkText);
        }

        return replacements;
    }

    /**
     * Creates an ItemStack for a static GUI item.
     */
    private ItemStack createStaticItem(GuiItem guiItem, Map<String, String> replacements) {
        GuiItem processedItem = guiItem.withReplacements(replacements);
        String materialStr = processedItem.getMaterialString();

        ItemStack item;
        if (materialStr.toLowerCase().startsWith("texture-")) {
            String texture = materialStr.substring("texture-".length());
            item = SkullUtils.createCustomHead(texture);
        } else {
            try {
                Material material = Material.valueOf(materialStr.toUpperCase());
                item = new ItemStack(material);
            } catch (IllegalArgumentException e) {
                item = new ItemStack(Material.STONE);
            }
        }

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

        if (processedItem.hasActions()) {
            builder.data(plugin, "gui_action", guiItem.key());
        }

        return builder.build();
    }

    /**
     * Creates an ItemStack for a perk display using the configurable template.
     */
    @SuppressWarnings("unchecked")
    private ItemStack createPerkItem(PerkDefinition perk, boolean isEquipped, ActivePerk equippedPerk) {
        // Get category info
        String categoryColor = messagesConfig.getCategoryColor(perk.category());
        String categoryName = messagesConfig.getCategoryName(perk.category());

        // Determine material
        ItemStack item;
        if (perk.hasCustomTexture()) {
            item = SkullUtils.createCustomHead(perk.getTextureValue());
        } else {
            item = new ItemStack(perk.getMaterial());
        }

        ItemBuilder builder = new ItemBuilder(item);

        // Get template from config
        GuiDefinition guiDef = configManager.getGuiConfig().getPerksListGui();
        Map<String, Object> template = guiDef.getPerkItemTemplate();

        // Build replacement map for placeholders
        Map<String, String> replacements = new HashMap<>();
        replacements.put("category_color", categoryColor);
        replacements.put("display_name", perk.displayName());
        replacements.put("equipped_suffix", isEquipped ? " " + messagesConfig.get("gui.equipped-suffix") : "");
        replacements.put("category_name", categoryName);
        replacements.put("description", perk.description() != null ? perk.description() : "");

        // Chance info
        double chance = perkManager.getPerkChance(perk.key(), currentToolType);
        replacements.put("chance", String.format("%.2f", chance));

        // Get level format strings from template (with fallbacks)
        String levelFormat = template != null ?
            (String) template.getOrDefault("level-format", "&8 - &7Nivel {level}: &a{boost}") :
            "&8 - &7Nivel {level}: &a{boost}";
        String levelCurrentFormat = template != null ?
            (String) template.getOrDefault("level-current-format", "&8 - &e&lNivel {level}: &a&l{boost} &7(Actual)") :
            "&8 - &e&lNivel {level}: &a&l{boost} &7(Actual)";
        String equippedText = template != null ?
            (String) template.getOrDefault("equipped-text", "&a&l✓ EQUIPADO") :
            "&a&l✓ EQUIPADO";

        // Build level info string using friendly display names
        StringBuilder levelInfo = new StringBuilder();
        for (int level = 1; level <= perk.getMaxLevel(); level++) {
            PerksConfig.PerkLevel perkLevel = perk.getLevel(level);
            if (perkLevel != null) {
                // Build boost description with friendly names
                String boostDesc = buildBoostDescription(perkLevel);

                // Choose format based on whether this is the equipped level
                String format;
                if (isEquipped && equippedPerk != null && equippedPerk.getLevel() == level) {
                    format = levelCurrentFormat;
                } else {
                    format = levelFormat;
                }

                // Replace level placeholders (including category_color)
                String levelLine = format
                    .replace("{level}", String.valueOf(level))
                    .replace("{boost}", boostDesc)
                    .replace("{category_color}", categoryColor);

                if (levelInfo.length() > 0) levelInfo.append("\n");
                levelInfo.append(levelLine);
            }
        }
        replacements.put("level_info", levelInfo.toString());

        // Equipped status - full line or empty
        replacements.put("equipped_status", isEquipped ? equippedText : "");

        // Build name from template
        String nameTemplate = template != null ?
            (String) template.getOrDefault("name", "{category_color}{display_name}{equipped_suffix}") :
            "{category_color}{display_name}{equipped_suffix}";
        String name = MessageUtils.replace(nameTemplate, replacements);
        builder.name(messagesConfig.translateColors(name));

        // Build lore from template
        List<String> loreTemplate = template != null ?
            (List<String>) template.getOrDefault("lore", getDefaultLoreTemplate()) :
            getDefaultLoreTemplate();

        List<String> lore = new ArrayList<>();
        for (String line : loreTemplate) {
            // IMPORTANT: Check ORIGINAL line for placeholders BEFORE replacement
            // because MessageUtils.replace will replace {level_info} with multi-line content

            // Handle {level_info} which can be multi-line
            if (line.contains("{level_info}")) {
                // Split level_info into separate lines and add each one
                String levelInfoContent = replacements.get("level_info");
                if (levelInfoContent != null && !levelInfoContent.isEmpty()) {
                    String[] levelLines = levelInfoContent.split("\n");
                    for (String levelLine : levelLines) {
                        if (!levelLine.isEmpty()) {
                            lore.add(messagesConfig.translateColors(levelLine));
                        }
                    }
                }
            } else if (line.contains("{equipped_status}")) {
                // Only add equipped status line if player has perk equipped
                if (isEquipped) {
                    String processedLine = MessageUtils.replace(line, replacements);
                    lore.add(messagesConfig.translateColors(processedLine.replace("{equipped_status}", equippedText)));
                }
            } else if (line.contains("{description}")) {
                // Only add description if not empty
                String desc = perk.description();
                if (desc != null && !desc.isEmpty()) {
                    String processedLine = MessageUtils.replace(line, replacements);
                    lore.add(messagesConfig.translateColors(processedLine.replace("{description}", desc)));
                }
            } else {
                // Regular line - process replacements normally
                String processedLine = MessageUtils.replace(line, replacements);
                lore.add(messagesConfig.translateColors(processedLine));
            }
        }

        // Remove trailing empty lines
        while (!lore.isEmpty() && lore.get(lore.size() - 1).trim().isEmpty()) {
            lore.remove(lore.size() - 1);
        }

        builder.lore(lore);

        // Issue #3: Hide all vanilla attributes by default
        builder.hideAll();

        // Add glow effect if equipped
        if (isEquipped) {
            builder.glow();
        }

        // Store perk key in item
        builder.data(plugin, "perk_key", perk.key());

        return builder.build();
    }

    /**
     * Builds a boost description using friendly display names from BoosterManager.
     */
    private String buildBoostDescription(PerksConfig.PerkLevel perkLevel) {
        Map<String, Double> boostMap = perkLevel.getBoostMap();
        StringBuilder sb = new StringBuilder();
        for (var entry : boostMap.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            String displayName = boosterManager.getBoostDisplayName(entry.getKey());
            // Format boost value - show as integer if it's a whole number
            double value = entry.getValue();
            String valueStr = (value == Math.floor(value)) ? String.valueOf((int) value) : String.valueOf(value);
            sb.append("+").append(valueStr).append("% ").append(displayName);
        }
        return sb.toString();
    }

    /**
     * Returns the default lore template if none is configured.
     */
    private List<String> getDefaultLoreTemplate() {
        return List.of(
            "&7Categoria: {category_color}{category_name}",
            "",
            "{description}",
            "",
            "&e&lNiveles:",
            "{level_info}",
            "",
            "&7Probabilidad: &f{chance}%",
            "{equipped_status}"
        );
    }

    /**
     * Handles a click in the perks list GUI.
     *
     * @param player The player
     * @param slot   The clicked slot
     * @param item   The clicked item
     */
    public void handleClick(Player player, int slot, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        GuiDefinition guiDef = configManager.getGuiConfig().getPerksListGui();

        // Check for static item actions
        for (GuiItem guiItem : guiDef.items().values()) {
            if (guiItem.slots().contains(slot)) {
                List<String> actions = guiItem.getActions("click");
                for (String action : actions) {
                    executeAction(player, action);
                }
                return;
            }
        }

        // Perk item click - just informational, no action needed
    }

    /**
     * Executes a GUI action.
     */
    private void executeAction(Player player, String action) {
        if (action == null || action.equalsIgnoreCase("none")) {
            return;
        }

        String actionLower = action.toLowerCase();

        // Back to main GUI
        if (actionLower.equals("back") || actionLower.equals("open-gui:main")) {
            guiManager.openMainGui(player, currentToolType);
            return;
        }

        // Open another GUI
        if (actionLower.startsWith("open-gui:")) {
            String guiName = action.substring(9).trim();
            switch (guiName.toLowerCase()) {
                case "main", "main-gui" -> guiManager.openMainGui(player, currentToolType);
            }
            return;
        }

        // Close
        if (actionLower.equals("close")) {
            player.closeInventory();
        }
    }

    /**
     * Formats a tool type for display.
     */
    private String formatToolName(String toolType) {
        if (toolType == null) return "Unknown";
        return configManager.getToolDisplayName(toolType);
    }

    public String getCurrentToolType() {
        return currentToolType;
    }
}
