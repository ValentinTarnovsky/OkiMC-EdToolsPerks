package com.okimc.edtoolsperks.managers;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.MessagesConfig;
import com.okimc.edtoolsperks.config.PerksConfig;
import com.okimc.edtoolsperks.config.PerksConfig.PerkDefinition;
import com.okimc.edtoolsperks.model.ActivePerk;
import com.okimc.edtoolsperks.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * Manages perk roll mechanics including gacha system with pity.
 *
 * <p>Core responsibilities:</p>
 * <ul>
 *   <li>Weighted random perk selection based on chances</li>
 *   <li>Pity system - guaranteed legendary after threshold</li>
 *   <li>Roll execution and perk assignment</li>
 *   <li>Command execution for perk rewards</li>
 * </ul>
 *
 * @author Snopeyy
 * @version 2.0.0
 */
public class PerkManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PerksConfig perksConfig;
    private final MessagesConfig messagesConfig;
    private final PlayerDataManager playerDataManager;
    private final BoosterManager boosterManager;

    private final Random random = ThreadLocalRandom.current();

    /**
     * Creates a new PerkManager instance.
     *
     * @param plugin            The main plugin instance
     * @param configManager     The configuration manager
     * @param playerDataManager The player data manager
     * @param boosterManager    The booster manager
     */
    public PerkManager(JavaPlugin plugin, ConfigManager configManager,
                       PlayerDataManager playerDataManager, BoosterManager boosterManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.perksConfig = configManager.getPerksConfig();
        this.messagesConfig = configManager.getMessagesConfig();
        this.playerDataManager = playerDataManager;
        this.boosterManager = boosterManager;
    }

    /**
     * Executes a perk roll for a player.
     *
     * @param player   The player rolling
     * @param toolType The tool type to roll for
     * @return CompletableFuture with the RollResult
     */
    public CompletableFuture<RollResult> roll(Player player, String toolType) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataManager.getData(uuid);

        if (data == null) {
            return CompletableFuture.completedFuture(
                RollResult.failure(RollResult.FailureReason.DATA_NOT_LOADED)
            );
        }

        // Check if player has rolls
        if (!data.hasRolls(1)) {
            return CompletableFuture.completedFuture(
                RollResult.failure(RollResult.FailureReason.NO_ROLLS)
            );
        }

        // Get available perks for this tool type
        List<PerkDefinition> availablePerks = perksConfig.getPerksForTool(toolType);
        if (availablePerks.isEmpty()) {
            return CompletableFuture.completedFuture(
                RollResult.failure(RollResult.FailureReason.NO_PERKS_AVAILABLE)
            );
        }

        // Consume roll
        data.consumeRoll();

        // Increment pity
        data.incrementPity();

        // Select perk (with pity check)
        PerkDefinition selectedPerk = selectPerk(data, toolType, availablePerks);

        // Check if pity was triggered (for legendary)
        boolean pityTriggered = false;
        String guaranteedCategory = configManager.getPityGuaranteedCategory();

        if (selectedPerk.category().equalsIgnoreCase(guaranteedCategory)) {
            // Reset pity when getting guaranteed category
            pityTriggered = data.getPityCount() >= configManager.getPityThreshold();
            data.resetPity();
        }

        // Determine level randomly from 1 to maxLevel
        int maxLevel = selectedPerk.getMaxLevel();
        int level = random.nextInt(maxLevel) + 1; // Random between 1 and maxLevel inclusive

        // Check if player already has a perk for this tool
        ActivePerk existingPerk = data.getPerk(toolType);
        ActivePerk previousPerk = existingPerk != null ? existingPerk.copy() : null;

        // Create new active perk
        ActivePerk newPerk = new ActivePerk(selectedPerk, toolType, level);

        // Update player data
        data.setPerk(newPerk);

        // Save to database and apply boosters
        // Note: savePlayer() saves all player data including perks, so no need for separate updatePerk call
        final boolean finalPityTriggered = pityTriggered;
        final ActivePerk finalPreviousPerk = previousPerk;

        return playerDataManager.savePlayer(data)
            .thenApply(v -> {
                // IMPORTANT: Schedule booster operations on main thread for Bukkit API safety
                // The thenApply callback runs on the database executor thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Remove old boosters if there was a previous perk
                    if (finalPreviousPerk != null) {
                        boosterManager.removeBoostersForTool(player, toolType);
                    }

                    // Apply new boosters
                    boosterManager.applyBoosters(player, newPerk);

                    // Execute update-tool command to refresh EdTools lore
                    executeUpdateToolCommand(player);
                });

                // Execute perk commands (already schedules to main thread internally)
                executeCommands(player, selectedPerk, level);

                if (configManager.isDebug()) {
                    plugin.getLogger().info("[DEBUG] " + player.getName() +
                        " rolled " + selectedPerk.displayName() +
                        " (category: " + selectedPerk.category() + ", pity: " + finalPityTriggered + ")");
                }

                return RollResult.success(newPerk, finalPreviousPerk, finalPityTriggered);
            })
            .exceptionally(e -> {
                plugin.getLogger().log(Level.SEVERE,
                    "Error during roll for " + player.getName(), e);
                return RollResult.failure(RollResult.FailureReason.DATABASE_ERROR);
            });
    }

    /**
     * Selects a perk using weighted random selection with pity system.
     *
     * @param data           The player's data
     * @param toolType       The tool type
     * @param availablePerks List of available perks
     * @return The selected PerkDefinition
     */
    private PerkDefinition selectPerk(PlayerData data, String toolType,
                                       List<PerkDefinition> availablePerks) {
        int pityThreshold = configManager.getPityThreshold();
        String guaranteedCategory = configManager.getPityGuaranteedCategory();

        // Check if pity threshold reached - guarantee legendary
        if (data.getPityCount() >= pityThreshold) {
            List<PerkDefinition> guaranteedPerks = availablePerks.stream()
                .filter(p -> p.category().equalsIgnoreCase(guaranteedCategory))
                .toList();

            if (!guaranteedPerks.isEmpty()) {
                if (configManager.isDebug()) {
                    plugin.getLogger().info("[DEBUG] Pity triggered! Guaranteeing " +
                        guaranteedCategory + " perk.");
                }
                return guaranteedPerks.get(random.nextInt(guaranteedPerks.size()));
            }
        }

        // Normal weighted random selection
        return weightedRandomSelect(availablePerks);
    }

    /**
     * Performs weighted random selection from a list of perks.
     *
     * @param perks The list of perks to select from
     * @return The selected PerkDefinition
     */
    private PerkDefinition weightedRandomSelect(List<PerkDefinition> perks) {
        if (perks.isEmpty()) {
            throw new IllegalArgumentException("Cannot select from empty perk list");
        }

        if (perks.size() == 1) {
            return perks.get(0);
        }

        // Calculate total weight
        double totalWeight = perks.stream()
            .mapToDouble(PerkDefinition::chance)
            .sum();

        // Generate random value
        double roll = random.nextDouble() * totalWeight;

        // Find the selected perk
        double cumulative = 0;
        for (PerkDefinition perk : perks) {
            cumulative += perk.chance();
            if (roll < cumulative) {
                return perk;
            }
        }

        // Fallback to last perk (shouldn't happen due to floating point)
        return perks.get(perks.size() - 1);
    }

    /**
     * Executes commands associated with a perk.
     *
     * @param player The player
     * @param perk   The perk definition
     * @param level  The perk level
     */
    private void executeCommands(Player player, PerkDefinition perk, int level) {
        List<String> commands = perk.commands();
        if (commands == null || commands.isEmpty()) {
            return;
        }

        // Schedule command execution on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String command : commands) {
                try {
                    // Replace placeholders
                    String processed = command
                        .replace("{player}", player.getName())
                        .replace("{perk}", perk.key())
                        .replace("{perk_name}", perk.displayName())
                        .replace("{level}", String.valueOf(level))
                        .replace("{category}", perk.category());

                    // Execute command
                    if (processed.startsWith("[player]")) {
                        // Execute as player
                        String playerCmd = processed.substring("[player]".length()).trim();
                        player.performCommand(playerCmd);
                    } else if (processed.startsWith("[console]")) {
                        // Execute as console
                        String consoleCmd = processed.substring("[console]".length()).trim();
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCmd);
                    } else {
                        // Default: execute as console
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);
                    }

                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                        "Error executing perk command: " + command, e);
                }
            }
        });
    }

    /**
     * Executes the update-tool command if enabled.
     * This is used to refresh EdTools lore after a perk is assigned.
     *
     * @param player The player who rolled the perk
     */
    private void executeUpdateToolCommand(Player player) {
        if (!configManager.isUpdateToolEnabled()) {
            return;
        }

        String command = configManager.getUpdateToolCommand();
        if (command == null || command.isEmpty()) {
            return;
        }

        try {
            // Replace placeholders
            String processed = command.replace("{player}", player.getName());

            // Execute as console
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processed);

            if (configManager.isDebug()) {
                plugin.getLogger().info("[DEBUG] Executed update-tool command: " + processed);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                "Error executing update-tool command: " + command, e);
        }
    }

    /**
     * Assigns a specific perk to a player (admin command).
     *
     * @param player   The player
     * @param toolType The tool type
     * @param perkKey  The perk key
     * @param level    The perk level
     * @return CompletableFuture with success status
     */
    public CompletableFuture<Boolean> assignPerk(Player player, String toolType,
                                                  String perkKey, int level) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataManager.getData(uuid);

        if (data == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Get perk definition
        PerkDefinition definition = perksConfig.getPerk(perkKey);
        if (definition == null) {
            return CompletableFuture.completedFuture(false);
        }

        // Validate level
        int maxLevel = definition.getMaxLevel();
        int finalLevel = Math.min(Math.max(1, level), maxLevel);

        // Remove old boosters
        boosterManager.removeBoostersForTool(player, toolType);

        // Create and assign new perk
        ActivePerk newPerk = new ActivePerk(definition, toolType, finalLevel);
        data.setPerk(newPerk);

        return playerDataManager.updatePerk(uuid, newPerk)
            .thenApply(v -> {
                // Apply new boosters
                boosterManager.applyBoosters(player, newPerk);

                if (configManager.isDebug()) {
                    plugin.getLogger().info("[DEBUG] Admin assigned " + perkKey +
                        " lvl " + finalLevel + " to " + player.getName());
                }

                return true;
            })
            .exceptionally(e -> {
                plugin.getLogger().log(Level.SEVERE,
                    "Error assigning perk to " + player.getName(), e);
                return false;
            });
    }

    /**
     * Removes a perk from a player.
     *
     * @param player   The player
     * @param toolType The tool type
     * @return CompletableFuture with success status
     */
    public CompletableFuture<Boolean> removePerk(Player player, String toolType) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataManager.getData(uuid);

        if (data == null || !data.hasPerk(toolType)) {
            return CompletableFuture.completedFuture(false);
        }

        // Remove boosters
        boosterManager.removeBoostersForTool(player, toolType);

        // Remove from data
        data.removePerk(toolType);

        return playerDataManager.removePerk(uuid, toolType)
            .thenApply(v -> true)
            .exceptionally(e -> {
                plugin.getLogger().log(Level.SEVERE,
                    "Error removing perk from " + player.getName(), e);
                return false;
            });
    }

    /**
     * Upgrades a player's existing perk by one level.
     *
     * @param player   The player
     * @param toolType The tool type
     * @return CompletableFuture with the new level, or -1 if failed
     */
    public CompletableFuture<Integer> upgradePerk(Player player, String toolType) {
        UUID uuid = player.getUniqueId();
        PlayerData data = playerDataManager.getData(uuid);

        if (data == null) {
            return CompletableFuture.completedFuture(-1);
        }

        ActivePerk perk = data.getPerk(toolType);
        if (perk == null || !perk.canUpgrade()) {
            return CompletableFuture.completedFuture(-1);
        }

        // Remove old boosters
        boosterManager.removeBoostersForTool(player, toolType);

        // Upgrade
        perk.upgrade();
        int newLevel = perk.getLevel();

        return playerDataManager.updatePerk(uuid, perk)
            .thenApply(v -> {
                // Apply new boosters with upgraded level
                boosterManager.applyBoosters(player, perk);
                return newLevel;
            })
            .exceptionally(e -> {
                plugin.getLogger().log(Level.SEVERE,
                    "Error upgrading perk for " + player.getName(), e);
                return -1;
            });
    }

    /**
     * Gets the chance percentage for a specific perk.
     *
     * @param perkKey  The perk key
     * @param toolType The tool type
     * @return The chance as a percentage (0-100), or 0 if not found
     */
    public double getPerkChance(String perkKey, String toolType) {
        PerkDefinition perk = perksConfig.getPerk(perkKey);
        if (perk == null) {
            return 0;
        }

        double totalWeight = perksConfig.getTotalWeightForTool(toolType);
        if (totalWeight <= 0) {
            return 0;
        }

        return (perk.chance() / totalWeight) * 100;
    }

    /**
     * Gets all perks for a tool type sorted by category order.
     *
     * @param toolType The tool type
     * @return Sorted list of perks
     */
    public List<PerkDefinition> getSortedPerksForTool(String toolType) {
        List<PerkDefinition> perks = new ArrayList<>(perksConfig.getPerksForTool(toolType));

        // Get category order from GUI config
        List<String> categoryOrder = configManager.getGuiConfig()
            .getPerksListGui()
            .getCategoryOrder();

        if (categoryOrder.isEmpty()) {
            return perks;
        }

        // Sort by category order
        perks.sort((a, b) -> {
            int indexA = categoryOrder.indexOf(a.category().toLowerCase());
            int indexB = categoryOrder.indexOf(b.category().toLowerCase());

            // Unknown categories go to end
            if (indexA < 0) indexA = Integer.MAX_VALUE;
            if (indexB < 0) indexB = Integer.MAX_VALUE;

            return Integer.compare(indexA, indexB);
        });

        return perks;
    }

    /**
     * Reapplies boosters for all online players.
     * Called after config reload.
     */
    public void reapplyAllBoosters() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerData data = playerDataManager.getData(player.getUniqueId());
            if (data == null) continue;

            // Reapply boosters for each perk
            for (ActivePerk perk : data.getAllPerks().values()) {
                boosterManager.removeBoostersForTool(player, perk.getToolType());
                boosterManager.applyBoosters(player, perk);
            }
        }

        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] Reapplied boosters for all online players.");
        }
    }

    /**
     * Gets a perk definition by key.
     *
     * @param perkKey The perk key
     * @return The PerkDefinition or null
     */
    public PerkDefinition getPerkDefinition(String perkKey) {
        return perksConfig.getPerk(perkKey);
    }

    /**
     * Gets all available tool types.
     *
     * @return Set of tool type strings
     */
    public Set<String> getToolTypes() {
        return perksConfig.getToolTypes();
    }

    /**
     * Gets all perk keys.
     *
     * @return Set of perk key strings
     */
    public Set<String> getAllPerkKeys() {
        Set<String> keys = new HashSet<>();
        for (PerkDefinition perk : perksConfig.getAllPerks()) {
            keys.add(perk.key());
        }
        return keys;
    }

    // ==================== Roll Result ====================

    /**
     * Represents the result of a perk roll.
     */
    public static class RollResult {

        private final boolean success;
        private final ActivePerk perk;
        private final ActivePerk previousPerk;
        private final boolean pityTriggered;
        private final FailureReason failureReason;

        private RollResult(boolean success, ActivePerk perk, ActivePerk previousPerk,
                          boolean pityTriggered, FailureReason failureReason) {
            this.success = success;
            this.perk = perk;
            this.previousPerk = previousPerk;
            this.pityTriggered = pityTriggered;
            this.failureReason = failureReason;
        }

        public static RollResult success(ActivePerk perk, ActivePerk previousPerk,
                                         boolean pityTriggered) {
            return new RollResult(true, perk, previousPerk, pityTriggered, null);
        }

        public static RollResult failure(FailureReason reason) {
            return new RollResult(false, null, null, false, reason);
        }

        public boolean isSuccess() {
            return success;
        }

        public ActivePerk getPerk() {
            return perk;
        }

        public ActivePerk getPreviousPerk() {
            return previousPerk;
        }

        public boolean hadPreviousPerk() {
            return previousPerk != null;
        }

        public boolean wasPityTriggered() {
            return pityTriggered;
        }

        public FailureReason getFailureReason() {
            return failureReason;
        }

        /**
         * Reasons a roll can fail.
         */
        public enum FailureReason {
            NO_ROLLS,
            NO_PERKS_AVAILABLE,
            DATA_NOT_LOADED,
            DATABASE_ERROR
        }
    }
}
