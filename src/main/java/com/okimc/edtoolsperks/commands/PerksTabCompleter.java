package com.okimc.edtoolsperks.commands;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.PerksConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tab completer for the /perks command.
 * Provides context-aware suggestions for all subcommands.
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class PerksTabCompleter implements TabCompleter {

    private final ConfigManager configManager;
    private final PerksConfig perksConfig;

    // Subcommands available to regular users
    private static final List<String> USER_SUBCOMMANDS = Arrays.asList(
        "gui", "info", "help"
    );

    // Subcommands available to admins
    private static final List<String> ADMIN_SUBCOMMANDS = Arrays.asList(
        "gui", "info", "help",
        "reload", "addrolls", "setrolls", "removerolls",
        "setperk", "removeperk", "resetpity", "debug"
    );

    // Default tool types for suggestions (used if config is empty)
    private static final List<String> DEFAULT_TOOL_TYPES = Arrays.asList(
        "pickaxe", "axe", "shovel", "hoe", "sword", "rod"
    );

    /**
     * Creates a new PerksTabCompleter.
     *
     * @param configManager The configuration manager
     */
    public PerksTabCompleter(ConfigManager configManager) {
        this.configManager = configManager;
        this.perksConfig = configManager.getPerksConfig();
    }

    /**
     * Gets tool types from config, falling back to defaults if empty.
     *
     * @return List of tool type strings
     */
    private List<String> getToolTypes() {
        var toolTypes = perksConfig.getToolTypes();
        if (toolTypes == null || toolTypes.isEmpty()) {
            return DEFAULT_TOOL_TYPES;
        }
        return new ArrayList<>(toolTypes);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // First argument - subcommand
            List<String> subCommands = sender.hasPermission("edtoolsperks.admin")
                ? ADMIN_SUBCOMMANDS
                : USER_SUBCOMMANDS;
            return filterStartsWith(subCommands, args[0]);
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "info" -> {
                if (args.length == 2 && sender.hasPermission("edtoolsperks.admin")) {
                    // Player name for info
                    return filterStartsWith(getOnlinePlayerNames(), args[1]);
                }
            }

            case "addrolls", "add", "setrolls", "set", "removerolls", "remove", "take" -> {
                if (!sender.hasPermission("edtoolsperks.admin")) {
                    return completions;
                }
                if (args.length == 2) {
                    // Player name
                    return filterStartsWith(getOnlinePlayerNames(), args[1]);
                }
                if (args.length == 3) {
                    // Amount suggestions
                    return filterStartsWith(Arrays.asList("1", "5", "10", "25", "50", "100"), args[2]);
                }
            }

            case "setperk" -> {
                if (!sender.hasPermission("edtoolsperks.admin")) {
                    return completions;
                }
                if (args.length == 2) {
                    // Player name
                    return filterStartsWith(getOnlinePlayerNames(), args[1]);
                }
                if (args.length == 3) {
                    // Tool type
                    return filterStartsWith(getToolTypes(), args[2]);
                }
                if (args.length == 4) {
                    // Perk key - filter by tool if possible
                    String toolType = args[2].toLowerCase();
                    List<String> perks = getPerkKeysForTool(toolType);
                    if (perks.isEmpty()) {
                        perks = getAllPerkKeys();
                    }
                    return filterStartsWith(perks, args[3]);
                }
                if (args.length == 5) {
                    // Level - get max level for the perk
                    String perkKey = args[3].toLowerCase();
                    PerksConfig.PerkDefinition perk = perksConfig.getPerk(perkKey);
                    if (perk != null) {
                        List<String> levels = new ArrayList<>();
                        for (int i = 1; i <= perk.getMaxLevel(); i++) {
                            levels.add(String.valueOf(i));
                        }
                        return filterStartsWith(levels, args[4]);
                    }
                    return filterStartsWith(Arrays.asList("1", "2", "3", "4", "5"), args[4]);
                }
            }

            case "removeperk", "clearperk" -> {
                if (!sender.hasPermission("edtoolsperks.admin")) {
                    return completions;
                }
                if (args.length == 2) {
                    // Player name
                    return filterStartsWith(getOnlinePlayerNames(), args[1]);
                }
                if (args.length == 3) {
                    // Tool type
                    return filterStartsWith(getToolTypes(), args[2]);
                }
            }

            case "resetpity" -> {
                if (!sender.hasPermission("edtoolsperks.admin")) {
                    return completions;
                }
                if (args.length == 2) {
                    // Player name
                    return filterStartsWith(getOnlinePlayerNames(), args[1]);
                }
            }

            case "reload", "debug", "help", "gui", "menu" -> {
                // No further arguments
                return completions;
            }
        }

        return completions;
    }

    /**
     * Gets a list of online player names.
     */
    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .collect(Collectors.toList());
    }

    /**
     * Gets all perk keys from config.
     */
    private List<String> getAllPerkKeys() {
        return perksConfig.getAllPerks().stream()
            .map(PerksConfig.PerkDefinition::key)
            .collect(Collectors.toList());
    }

    /**
     * Gets perk keys for a specific tool type.
     */
    private List<String> getPerkKeysForTool(String toolType) {
        return perksConfig.getPerksForTool(toolType).stream()
            .map(PerksConfig.PerkDefinition::key)
            .collect(Collectors.toList());
    }

    /**
     * Filters a list to only include entries starting with a prefix.
     */
    private List<String> filterStartsWith(List<String> list, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return new ArrayList<>(list);
        }
        String lowerPrefix = prefix.toLowerCase();
        return list.stream()
            .filter(s -> s.toLowerCase().startsWith(lowerPrefix))
            .collect(Collectors.toList());
    }
}
