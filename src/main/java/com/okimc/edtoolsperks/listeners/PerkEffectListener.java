package com.okimc.edtoolsperks.listeners;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.integration.EdToolsIntegration;
import com.okimc.edtoolsperks.managers.BoosterManager;
import com.okimc.edtoolsperks.managers.PlayerDataManager;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listens for EdTools block break events.
 *
 * <p><b>IMPORTANT:</b> Direct boost application has been DISABLED because boosts are now
 * properly handled via {@link BoosterManager} using the EdToolsBoostersAPI.
 * The EdToolsBoostersAPI multiplier is applied automatically by EdTools when
 * currency is earned, so we don't need to add extra currency manually.</p>
 *
 * <p>This listener is kept for potential future use (e.g., perk commands on block break)
 * but currently does not apply any boosts to avoid double-application.</p>
 *
 * @author Snopeyy
 * @version 2.0.0
 */
public class PerkEffectListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final EdToolsIntegration edToolsIntegration;
    private final PlayerDataManager playerDataManager;
    private final BoosterManager boosterManager;

    /**
     * Creates a new PerkEffectListener.
     *
     * <p>Note: This listener no longer applies direct boosts. Boosts are handled
     * exclusively by EdToolsBoostersAPI via BoosterManager to prevent double application.</p>
     *
     * @param plugin             The main plugin instance
     * @param configManager      The configuration manager
     * @param edToolsIntegration The EdTools integration
     * @param playerDataManager  The player data manager
     * @param boosterManager     The booster manager
     */
    public PerkEffectListener(JavaPlugin plugin, ConfigManager configManager,
                               EdToolsIntegration edToolsIntegration,
                               PlayerDataManager playerDataManager,
                               BoosterManager boosterManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.edToolsIntegration = edToolsIntegration;
        this.playerDataManager = playerDataManager;
        this.boosterManager = boosterManager;

        if (configManager.isDebug()) {
            plugin.getLogger().info("[DEBUG] PerkEffectListener initialized. " +
                "Direct boosts DISABLED - using EdToolsBoostersAPI instead.");
        }
    }

    // ====================================================================
    // DIRECT BOOST APPLICATION HAS BEEN REMOVED
    // ====================================================================
    //
    // Previously, this listener would add currency directly via:
    //   currencyAPI.addCurrency(player.getUniqueId(), boostType, bonusAmount);
    //
    // This caused DOUBLE BOOSTS because:
    // 1. BoosterManager adds a booster via EdToolsBoostersAPI.addBooster()
    //    which makes EdTools multiply earnings automatically
    // 2. This listener was ALSO adding extra currency on top
    //
    // Now boosts are handled ONLY by EdToolsBoostersAPI via BoosterManager.
    // The multiplier (e.g., x2 for 100%, x3 for 200%) is applied automatically
    // by EdTools when currency is earned from block breaks.
    //
    // If you need direct currency additions for special cases in the future,
    // you can re-implement the onEdToolsBreak handler, but make sure to
    // coordinate with BoosterManager to avoid double-application.
    // ====================================================================
}
