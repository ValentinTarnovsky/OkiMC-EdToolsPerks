package com.okimc.edtoolsperks.listeners;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.MessagesConfig;
import com.okimc.edtoolsperks.managers.BoosterManager;
import com.okimc.edtoolsperks.managers.PlayerDataManager;
import com.okimc.edtoolsperks.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles player connection events for data loading and unloading.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Load player data asynchronously on join</li>
 *   <li>Apply active boosters after data load</li>
 *   <li>Save and unload player data on quit</li>
 *   <li>Clean up boosters on disconnect</li>
 * </ul>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class PlayerConnectionListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final BoosterManager boosterManager;
    private final MessagesConfig messagesConfig;

    /**
     * Creates a new PlayerConnectionListener.
     *
     * @param plugin            The main plugin instance
     * @param configManager     The configuration manager
     * @param playerDataManager The player data manager
     * @param boosterManager    The booster manager
     */
    public PlayerConnectionListener(JavaPlugin plugin, ConfigManager configManager,
                                     PlayerDataManager playerDataManager,
                                     BoosterManager boosterManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.playerDataManager = playerDataManager;
        this.boosterManager = boosterManager;
        this.messagesConfig = configManager.getMessagesConfig();
    }

    /**
     * Handles player join event.
     * Loads player data asynchronously and applies boosters.
     *
     * @param event The join event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Load player data asynchronously
        playerDataManager.loadPlayer(player.getUniqueId()).thenAccept(playerData -> {
            if (playerData == null) {
                // Data failed to load - notify if debug enabled
                if (configManager.isDebug()) {
                    plugin.getLogger().warning("Failed to load data for player: " + player.getName());
                }
                return;
            }

            // Apply boosters on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                // Apply all active boosters for this player
                applyPlayerBoosters(player, playerData);

                // Send welcome message if configured
                if (configManager.isSendJoinMessage()) {
                    sendJoinInfo(player, playerData);
                }

                if (configManager.isDebug()) {
                    plugin.getLogger().info("Loaded data for player: " + player.getName() +
                        " (Rolls: " + playerData.getRolls() + ", Perks: " + playerData.getAllPerks().size() + ")");
                }
            });
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error loading player data for " + player.getName() + ": " + ex.getMessage());
            if (configManager.isDebug()) {
                ex.printStackTrace();
            }
            return null;
        });
    }

    /**
     * Handles player quit event.
     * Saves and unloads player data, removes boosters.
     *
     * @param event The quit event
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Remove all boosters for this player
        boosterManager.removeAllBoosters(player);

        // Save and unload player data
        playerDataManager.unloadPlayer(player.getUniqueId()).thenRun(() -> {
            if (configManager.isDebug()) {
                plugin.getLogger().info("Saved and unloaded data for player: " + player.getName());
            }
        }).exceptionally(ex -> {
            plugin.getLogger().severe("Error saving player data for " + player.getName() + ": " + ex.getMessage());
            if (configManager.isDebug()) {
                ex.printStackTrace();
            }
            return null;
        });
    }

    /**
     * Applies all active boosters for a player.
     *
     * @param player     The player
     * @param playerData The player's data
     */
    private void applyPlayerBoosters(Player player, PlayerData playerData) {
        // Get all active perks and apply their boosters
        playerData.getAllPerks().forEach((toolType, perk) -> {
            if (perk != null && perk.hasDefinition()) {
                boosterManager.applyBoosters(player, perk);
            }
        });
    }

    /**
     * Sends join information to the player.
     *
     * @param player     The player
     * @param playerData The player's data
     */
    private void sendJoinInfo(Player player, PlayerData playerData) {
        int rolls = playerData.getRolls();
        int perkCount = playerData.getAllPerks().size();

        // Only send if player has rolls or perks
        if (rolls > 0 || perkCount > 0) {
            messagesConfig.send(player, "join.welcome",
                "rolls", String.valueOf(rolls),
                "perks", String.valueOf(perkCount));
        }
    }

    /**
     * Loads data for all online players.
     * Called during plugin enable to handle reloads.
     */
    public void loadAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            playerDataManager.loadPlayer(player.getUniqueId()).thenAccept(playerData -> {
                if (playerData != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            applyPlayerBoosters(player, playerData);
                        }
                    });
                }
            });
        }
    }

    /**
     * Saves data for all online players.
     * Called during plugin disable.
     */
    public void saveAllOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Remove boosters synchronously
            boosterManager.removeAllBoosters(player);

            // Save data (will be done during PlayerDataManager shutdown)
        }
    }
}
