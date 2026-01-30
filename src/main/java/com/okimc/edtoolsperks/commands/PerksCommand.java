package com.okimc.edtoolsperks.commands;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.MessagesConfig;
import com.okimc.edtoolsperks.config.PerksConfig;
import com.okimc.edtoolsperks.gui.GuiManager;
import com.okimc.edtoolsperks.integration.EdToolsIntegration;
import com.okimc.edtoolsperks.listeners.InventoryClickListener;
import com.okimc.edtoolsperks.managers.BoosterManager;
import com.okimc.edtoolsperks.managers.PerkManager;
import com.okimc.edtoolsperks.managers.PlayerDataManager;
import com.okimc.edtoolsperks.model.ActivePerk;
import com.okimc.edtoolsperks.model.PlayerData;
import com.okimc.edtoolsperks.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Main command handler for EdToolsPerks plugin.
 *
 * <p>Commands:</p>
 * <ul>
 *   <li>/perks - Open main GUI (player)</li>
 *   <li>/perks reload - Reload configurations (admin)</li>
 *   <li>/perks info [player] - View perk info</li>
 *   <li>/perks addrolls [player] [amount] - Add rolls to player (admin)</li>
 *   <li>/perks setrolls [player] [amount] - Set player rolls (admin)</li>
 *   <li>/perks removerolls [player] [amount] - Remove rolls (admin)</li>
 *   <li>/perks setperk [player] [tool] [perk] [level] - Set perk (admin)</li>
 *   <li>/perks removeperk [player] [tool] - Remove perk (admin)</li>
 *   <li>/perks resetpity [player] - Reset pity counter (admin)</li>
 *   <li>/perks debug - Toggle debug mode (admin)</li>
 * </ul>
 *
 * @author Snopeyy
 * @version 2.0.0
 */
public class PerksCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final PerkManager perkManager;
    private final BoosterManager boosterManager;
    private final GuiManager guiManager;
    private final EdToolsIntegration edToolsIntegration;
    private final InventoryClickListener inventoryClickListener;
    private final MessagesConfig messagesConfig;
    private final PerksConfig perksConfig;

    /**
     * Creates a new PerksCommand instance.
     */
    public PerksCommand(JavaPlugin plugin, ConfigManager configManager,
                        PlayerDataManager playerDataManager, PerkManager perkManager,
                        BoosterManager boosterManager, GuiManager guiManager,
                        EdToolsIntegration edToolsIntegration,
                        InventoryClickListener inventoryClickListener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.perkManager = perkManager;
        this.boosterManager = boosterManager;
        this.guiManager = guiManager;
        this.edToolsIntegration = edToolsIntegration;
        this.inventoryClickListener = inventoryClickListener;
        this.messagesConfig = configManager.getMessagesConfig();
        this.perksConfig = configManager.getPerksConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // No arguments - open GUI
        if (args.length == 0) {
            return handleOpenGui(sender);
        }

        String subCommand = args[0].toLowerCase();

        return switch (subCommand) {
            case "reload" -> handleReload(sender);
            case "info" -> handleInfo(sender, args);
            case "addrolls", "add" -> handleAddRolls(sender, args);
            case "setrolls", "set" -> handleSetRolls(sender, args);
            case "removerolls", "remove", "take" -> handleRemoveRolls(sender, args);
            case "setperk" -> handleSetPerk(sender, args);
            case "removeperk", "clearperk" -> handleRemovePerk(sender, args);
            case "resetpity" -> handleResetPity(sender, args);
            case "debug" -> handleDebug(sender);
            case "help", "?" -> handleHelp(sender);
            case "gui", "menu" -> handleOpenGui(sender);
            default -> {
                messagesConfig.send(sender, "commands.unknown-subcommand", "command", subCommand);
                yield true;
            }
        };
    }

    /**
     * Opens the main GUI for the player.
     */
    private boolean handleOpenGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messagesConfig.send(sender, "commands.players-only");
            return true;
        }

        if (!player.hasPermission("edtoolsperks.use")) {
            messagesConfig.send(player, "commands.no-permission");
            return true;
        }

        // Check player data
        PlayerData data = playerDataManager.getData(player.getUniqueId());
        if (data == null) {
            messagesConfig.send(player, "errors.data-not-loaded");
            return true;
        }

        // Determine tool type from held item
        ItemStack heldItem = player.getInventory().getItemInMainHand();

        // Check if player has a valid OmniTool in hand
        if (!edToolsIntegration.isOmniTool(heldItem)) {
            messagesConfig.send(player, "errors.no-tool-in-hand");
            return true;
        }

        String toolType = edToolsIntegration.getToolType(heldItem);

        // Store tool type for GUI context
        inventoryClickListener.setPlayerToolType(player, toolType);

        // Open GUI
        guiManager.openMainGui(player, toolType);
        return true;
    }

    /**
     * Handles the reload subcommand.
     */
    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("edtoolsperks.admin")) {
            messagesConfig.send(sender, "commands.no-permission");
            return true;
        }

        long start = System.currentTimeMillis();

        // Reload configurations
        boolean success = configManager.reloadAll();

        if (success) {
            // Reload related managers
            boosterManager.reload();
            guiManager.reload();

            // Relink perk definitions with updated config values
            playerDataManager.relinkPerkDefinitions();

            // Refresh boosters for all online players with new boost values
            // This ensures config changes (e.g., boost 20% -> 25%) apply immediately
            boosterManager.refreshAllOnlinePlayers(playerDataManager);

            long time = System.currentTimeMillis() - start;
            messagesConfig.send(sender, "commands.reload-success", "time", String.valueOf(time));
        } else {
            messagesConfig.send(sender, "commands.reload-failed");
        }

        return true;
    }

    /**
     * Handles the info subcommand.
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edtoolsperks.use")) {
            messagesConfig.send(sender, "commands.no-permission");
            return true;
        }

        Player target;
        if (args.length >= 2) {
            // Check admin permission for viewing others
            if (!sender.hasPermission("edtoolsperks.admin")) {
                messagesConfig.send(sender, "commands.no-permission");
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                messagesConfig.send(sender, "commands.player-not-found", "player", args[1]);
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                messagesConfig.send(sender, "commands.specify-player");
                return true;
            }
            target = (Player) sender;
        }

        PlayerData data = playerDataManager.getData(target.getUniqueId());
        if (data == null) {
            messagesConfig.send(sender, "errors.data-not-loaded");
            return true;
        }

        // Send info header
        messagesConfig.send(sender, "commands.info-header", "player", target.getName());
        messagesConfig.send(sender, "commands.info-rolls", "rolls", String.valueOf(data.getRolls()));
        messagesConfig.send(sender, "commands.info-pity",
            "pity", String.valueOf(data.getPityCount()),
            "threshold", String.valueOf(configManager.getPityThreshold()));

        // List perks
        messagesConfig.send(sender, "commands.info-perks-header");
        var perks = data.getAllPerks();
        if (perks.isEmpty()) {
            messagesConfig.send(sender, "commands.info-no-perks");
        } else {
            for (var entry : perks.entrySet()) {
                ActivePerk perk = entry.getValue();
                String color = messagesConfig.getCategoryColor(perk.getCategory());
                messagesConfig.send(sender, "commands.info-perk-line",
                    "tool", formatToolName(entry.getKey()),
                    "perk", color + perk.getDisplayName(),
                    "level", String.valueOf(perk.getLevel()));
            }
        }

        return true;
    }

    /**
     * Handles adding rolls to a player.
     */
    private boolean handleAddRolls(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edtoolsperks.admin")) {
            messagesConfig.send(sender, "commands.no-permission");
            return true;
        }

        if (args.length < 3) {
            messagesConfig.send(sender, "commands.usage-addrolls");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messagesConfig.send(sender, "commands.player-not-found", "player", args[1]);
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                messagesConfig.send(sender, "commands.invalid-amount");
                return true;
            }
        } catch (NumberFormatException e) {
            messagesConfig.send(sender, "commands.invalid-amount");
            return true;
        }

        PlayerData data = playerDataManager.getData(target.getUniqueId());
        if (data == null) {
            messagesConfig.send(sender, "errors.data-not-loaded");
            return true;
        }

        // Add rolls (playerDataManager.addRolls already updates cache, don't call data.addRolls)
        playerDataManager.addRolls(target.getUniqueId(), amount);

        messagesConfig.send(sender, "commands.rolls-added",
            "amount", String.valueOf(amount),
            "player", target.getName(),
            "total", String.valueOf(data.getRolls()));

        // Notify target
        if (!sender.equals(target)) {
            messagesConfig.send(target, "commands.rolls-received",
                "amount", String.valueOf(amount),
                "total", String.valueOf(data.getRolls()));
        }

        return true;
    }

    /**
     * Handles setting a player's rolls.
     */
    private boolean handleSetRolls(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edtoolsperks.admin")) {
            messagesConfig.send(sender, "commands.no-permission");
            return true;
        }

        if (args.length < 3) {
            messagesConfig.send(sender, "commands.usage-setrolls");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messagesConfig.send(sender, "commands.player-not-found", "player", args[1]);
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount < 0) {
                messagesConfig.send(sender, "commands.invalid-amount");
                return true;
            }
        } catch (NumberFormatException e) {
            messagesConfig.send(sender, "commands.invalid-amount");
            return true;
        }

        PlayerData data = playerDataManager.getData(target.getUniqueId());
        if (data == null) {
            messagesConfig.send(sender, "errors.data-not-loaded");
            return true;
        }

        // Set rolls (playerDataManager.setRolls already updates cache, don't call data.setRolls)
        playerDataManager.setRolls(target.getUniqueId(), amount);

        messagesConfig.send(sender, "commands.rolls-set",
            "amount", String.valueOf(amount),
            "player", target.getName());

        return true;
    }

    /**
     * Handles removing rolls from a player.
     */
    private boolean handleRemoveRolls(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edtoolsperks.admin")) {
            messagesConfig.send(sender, "commands.no-permission");
            return true;
        }

        if (args.length < 3) {
            messagesConfig.send(sender, "commands.usage-removerolls");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messagesConfig.send(sender, "commands.player-not-found", "player", args[1]);
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount <= 0) {
                messagesConfig.send(sender, "commands.invalid-amount");
                return true;
            }
        } catch (NumberFormatException e) {
            messagesConfig.send(sender, "commands.invalid-amount");
            return true;
        }

        PlayerData data = playerDataManager.getData(target.getUniqueId());
        if (data == null) {
            messagesConfig.send(sender, "errors.data-not-loaded");
            return true;
        }

        // Check if they have enough
        if (data.getRolls() < amount) {
            messagesConfig.send(sender, "commands.not-enough-rolls-target",
                "player", target.getName(),
                "has", String.valueOf(data.getRolls()));
            return true;
        }

        // Remove rolls (playerDataManager.removeRolls already updates cache, don't call data.removeRolls)
        playerDataManager.removeRolls(target.getUniqueId(), amount);

        messagesConfig.send(sender, "commands.rolls-removed",
            "amount", String.valueOf(amount),
            "player", target.getName(),
            "total", String.valueOf(data.getRolls()));

        return true;
    }

    /**
     * Handles setting a perk for a player.
     */
    private boolean handleSetPerk(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edtoolsperks.admin")) {
            messagesConfig.send(sender, "commands.no-permission");
            return true;
        }

        // /perks setperk <player> <tool> <perk> [level]
        if (args.length < 4) {
            messagesConfig.send(sender, "commands.usage-setperk");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messagesConfig.send(sender, "commands.player-not-found", "player", args[1]);
            return true;
        }

        String toolType = args[2].toLowerCase();
        String perkKey = args[3].toLowerCase();

        int level = 1;
        if (args.length >= 5) {
            try {
                level = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                messagesConfig.send(sender, "commands.invalid-level");
                return true;
            }
        }

        // Validate perk exists
        PerksConfig.PerkDefinition perkDef = perksConfig.getPerk(perkKey);
        if (perkDef == null) {
            messagesConfig.send(sender, "commands.perk-not-found", "perk", perkKey);
            return true;
        }

        // Validate level
        if (level < 1 || level > perkDef.getMaxLevel()) {
            messagesConfig.send(sender, "commands.invalid-level-range",
                "min", "1",
                "max", String.valueOf(perkDef.getMaxLevel()));
            return true;
        }

        PlayerData data = playerDataManager.getData(target.getUniqueId());
        if (data == null) {
            messagesConfig.send(sender, "errors.data-not-loaded");
            return true;
        }

        // Remove old perk boosters
        boosterManager.removeBoostersForTool(target, toolType);

        // Create and set new perk (use PerkDefinition constructor)
        ActivePerk newPerk = new ActivePerk(perkDef, toolType, level);
        data.setPerk(toolType, newPerk);

        // Save to database
        playerDataManager.savePerk(target.getUniqueId(), newPerk);

        // Apply new boosters
        boosterManager.applyBoosters(target, newPerk);

        String color = messagesConfig.getCategoryColor(perkDef.category());
        messagesConfig.send(sender, "commands.perk-set",
            "player", target.getName(),
            "tool", formatToolName(toolType),
            "perk", color + perkDef.displayName(),
            "level", String.valueOf(level));

        return true;
    }

    /**
     * Handles removing a perk from a player.
     */
    private boolean handleRemovePerk(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edtoolsperks.admin")) {
            messagesConfig.send(sender, "commands.no-permission");
            return true;
        }

        if (args.length < 3) {
            messagesConfig.send(sender, "commands.usage-removeperk");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messagesConfig.send(sender, "commands.player-not-found", "player", args[1]);
            return true;
        }

        String toolType = args[2].toLowerCase();

        PlayerData data = playerDataManager.getData(target.getUniqueId());
        if (data == null) {
            messagesConfig.send(sender, "errors.data-not-loaded");
            return true;
        }

        // Check if player has a perk for this tool
        ActivePerk currentPerk = data.getPerk(toolType);
        if (currentPerk == null) {
            messagesConfig.send(sender, "commands.no-perk-to-remove",
                "player", target.getName(),
                "tool", formatToolName(toolType));
            return true;
        }

        // Remove boosters
        boosterManager.removeBoostersForTool(target, toolType);

        // Remove perk
        data.removePerk(toolType);
        playerDataManager.removePerk(target.getUniqueId(), toolType);

        messagesConfig.send(sender, "commands.perk-removed",
            "player", target.getName(),
            "tool", formatToolName(toolType));

        return true;
    }

    /**
     * Handles resetting a player's pity counter.
     */
    private boolean handleResetPity(CommandSender sender, String[] args) {
        if (!sender.hasPermission("edtoolsperks.admin")) {
            messagesConfig.send(sender, "commands.no-permission");
            return true;
        }

        if (args.length < 2) {
            messagesConfig.send(sender, "commands.usage-resetpity");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messagesConfig.send(sender, "commands.player-not-found", "player", args[1]);
            return true;
        }

        PlayerData data = playerDataManager.getData(target.getUniqueId());
        if (data == null) {
            messagesConfig.send(sender, "errors.data-not-loaded");
            return true;
        }

        int oldPity = data.getPityCount();
        data.resetPity();
        playerDataManager.resetPity(target.getUniqueId());

        messagesConfig.send(sender, "commands.pity-reset",
            "player", target.getName(),
            "old_value", String.valueOf(oldPity));

        return true;
    }

    /**
     * Handles toggling debug mode.
     */
    private boolean handleDebug(CommandSender sender) {
        if (!sender.hasPermission("edtoolsperks.admin")) {
            messagesConfig.send(sender, "commands.no-permission");
            return true;
        }

        // Toggle debug in config
        boolean newState = !configManager.isDebug();
        configManager.getMainConfig().set("debug", newState);

        // Perform file I/O asynchronously to avoid blocking main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.saveConfig();

            // Reload on main thread (config managers may access Bukkit API)
            Bukkit.getScheduler().runTask(plugin, () -> {
                configManager.reloadAll();

                if (newState) {
                    messagesConfig.send(sender, "commands.debug-enabled");
                } else {
                    messagesConfig.send(sender, "commands.debug-disabled");
                }
            });
        });

        return true;
    }

    /**
     * Handles the help subcommand.
     */
    private boolean handleHelp(CommandSender sender) {
        messagesConfig.send(sender, "commands.help-header");
        messagesConfig.send(sender, "commands.help-gui");
        messagesConfig.send(sender, "commands.help-info");

        if (sender.hasPermission("edtoolsperks.admin")) {
            messagesConfig.send(sender, "commands.help-admin-header");
            messagesConfig.send(sender, "commands.help-reload");
            messagesConfig.send(sender, "commands.help-addrolls");
            messagesConfig.send(sender, "commands.help-setrolls");
            messagesConfig.send(sender, "commands.help-removerolls");
            messagesConfig.send(sender, "commands.help-setperk");
            messagesConfig.send(sender, "commands.help-removeperk");
            messagesConfig.send(sender, "commands.help-resetpity");
            messagesConfig.send(sender, "commands.help-debug");
        }

        return true;
    }

    /**
     * Formats a tool type for display.
     */
    private String formatToolName(String toolType) {
        if (toolType == null) return messagesConfig.get("fallback.unknown-tool", "Unknown");
        return configManager.getToolDisplayName(toolType);
    }
}
