package com.okimc.edtoolsperks.gui;

import com.okimc.edtoolsperks.config.ConfigManager;
import com.okimc.edtoolsperks.config.MessagesConfig;
import com.okimc.edtoolsperks.config.PerksConfig;
import com.okimc.edtoolsperks.managers.BoosterManager;
import com.okimc.edtoolsperks.managers.PerkManager;
import com.okimc.edtoolsperks.managers.PlayerDataManager;
import com.okimc.edtoolsperks.model.ActivePerk;
import com.okimc.edtoolsperks.model.PlayerData;
import com.okimc.edtoolsperks.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Animated GUI for the perk roll experience.
 * Shows a visual animation before revealing the rolled perk.
 *
 * Visual style matches the original EdToolsPerks animation:
 * - 45-slot inventory with tool in center
 * - Crystal slots animate with random materials
 * - Border remains static (black glass)
 * - On finish, shows category color on all crystal slots
 *
 * @author Snopeyy
 * @version 2.0.0
 */
public class AnimationGui {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final PlayerDataManager playerDataManager;
    private final PerkManager perkManager;
    private final BoosterManager boosterManager;
    private final GuiManager guiManager;
    private final MessagesConfig messagesConfig;
    private final PerksConfig perksConfig;

    // Animation state
    private BukkitTask animationTask;
    private Player animatingPlayer;
    private String toolType;
    private Inventory inventory;
    private boolean animationComplete;

    // Animation configuration (loaded from config)
    private int durationTicks;
    private int[] crystalSlots;
    private int[] borderSlots;
    private int toolSlot;
    private Material[] crystalMaterials;
    private Material borderMaterial;

    // Sound effects
    private Sound startSound;
    private int tickInterval;
    private Sound tickSound;
    private float tickVolume;

    // Time to display result before returning to main menu (in ticks)
    private int resultDisplayTicks;

    // Animation progress
    private int animationTick = 0;

    private final Random random = ThreadLocalRandom.current();

    // Default values matching old version
    private static final int[] DEFAULT_CRYSTAL_SLOTS = {12, 13, 14, 21, 23, 30, 31, 32};
    private static final int[] DEFAULT_BORDER_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 17,
        18, 26,
        27, 35,
        36, 44,
        45, 46, 47, 48, 49, 50, 51, 52, 53
    };
    private static final int DEFAULT_TOOL_SLOT = 22;
    private static final Material[] DEFAULT_CRYSTAL_MATERIALS = {
        Material.WHITE_STAINED_GLASS_PANE,
        Material.LIGHT_GRAY_STAINED_GLASS_PANE,
        Material.GRAY_STAINED_GLASS_PANE,
        Material.CYAN_STAINED_GLASS_PANE,
        Material.LIGHT_BLUE_STAINED_GLASS_PANE,
        Material.BLUE_STAINED_GLASS_PANE,
        Material.PURPLE_STAINED_GLASS_PANE,
        Material.MAGENTA_STAINED_GLASS_PANE
    };

    /**
     * Creates a new AnimationGui instance.
     */
    public AnimationGui(JavaPlugin plugin, ConfigManager configManager,
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

        // Load animation settings from config
        loadAnimationSettings();
    }

    /**
     * Loads animation settings from config.yml.
     * Falls back to defaults matching the old version if settings are missing.
     */
    private void loadAnimationSettings() {
        FileConfiguration config = configManager.getMainConfig();

        // Duration
        this.durationTicks = config.getInt("animation.duration-ticks", 60);

        // Crystal slots
        List<Integer> crystalSlotsList = config.getIntegerList("animation.crystal-slots");
        if (crystalSlotsList.isEmpty()) {
            this.crystalSlots = DEFAULT_CRYSTAL_SLOTS;
        } else {
            this.crystalSlots = crystalSlotsList.stream().mapToInt(Integer::intValue).toArray();
        }

        // Border slots
        List<Integer> borderSlotsList = config.getIntegerList("animation.border-slots");
        if (borderSlotsList.isEmpty()) {
            this.borderSlots = DEFAULT_BORDER_SLOTS;
        } else {
            this.borderSlots = borderSlotsList.stream().mapToInt(Integer::intValue).toArray();
        }

        // Tool slot (center)
        this.toolSlot = config.getInt("animation.tool-slot", DEFAULT_TOOL_SLOT);

        // Crystal materials
        List<String> crystalMaterialNames = config.getStringList("animation.crystal-materials");
        if (crystalMaterialNames.isEmpty()) {
            this.crystalMaterials = DEFAULT_CRYSTAL_MATERIALS;
        } else {
            this.crystalMaterials = crystalMaterialNames.stream()
                .map(name -> parseMaterial(name, Material.WHITE_STAINED_GLASS_PANE))
                .toArray(Material[]::new);
        }

        // Border material
        String borderMaterialName = config.getString("animation.border-material", "BLACK_STAINED_GLASS_PANE");
        this.borderMaterial = parseMaterial(borderMaterialName, Material.BLACK_STAINED_GLASS_PANE);

        // Sound effects
        String startSoundName = config.getString("animation.sound-effects.start", "BLOCK_BEACON_ACTIVATE");
        this.startSound = parseSound(startSoundName, Sound.BLOCK_BEACON_ACTIVATE);

        this.tickInterval = config.getInt("animation.sound-effects.tick-interval", 10);

        String tickSoundName = config.getString("animation.sound-effects.tick-sound", "UI_BUTTON_CLICK");
        this.tickSound = parseSound(tickSoundName, Sound.UI_BUTTON_CLICK);

        this.tickVolume = (float) config.getDouble("animation.sound-effects.tick-volume", 0.5);

        // Time to display result before returning to main menu
        this.resultDisplayTicks = config.getInt("animation.result-display-ticks", 60);
    }

    /**
     * Parses a Material from string, returning fallback if invalid.
     */
    private Material parseMaterial(String name, Material fallback) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Parses a Sound from string, returning fallback if invalid.
     */
    private Sound parseSound(String name, Sound fallback) {
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /**
     * Opens the animation GUI and starts the roll.
     *
     * @param player   The player
     * @param toolType The tool type for the roll
     */
    public void open(Player player, String toolType) {
        this.animatingPlayer = player;
        this.toolType = toolType;
        this.animationComplete = false;
        this.animationTick = 0;

        // Create inventory with size from guis.yml
        // Title is also read from guis.yml for consistency
        var animationGuiDef = configManager.getGuiConfig().getAnimationGui();
        String title = messagesConfig.translateColors(animationGuiDef.title());
        int size = animationGuiDef.size();
        this.inventory = Bukkit.createInventory(null, size, title);

        // Initialize layout
        createAnimationInventory();

        // Open inventory
        player.openInventory(inventory);

        // Play start sound
        playSound(player, startSound, 1.0f, 1.0f);

        // Start animation
        startAnimation();
    }

    /**
     * Creates the animation inventory layout.
     * Matches old version: border, crystals, and tool in center.
     */
    private void createAnimationInventory() {
        // Fill border with static black glass
        ItemStack borderItem = new ItemBuilder(borderMaterial)
            .name(" ")
            .build();

        for (int slot : borderSlots) {
            if (slot < inventory.getSize()) {
                inventory.setItem(slot, borderItem);
            }
        }

        // Tool display in center - shows player's held item
        ItemStack toolDisplay = animatingPlayer.getInventory().getItemInMainHand().clone();
        if (toolDisplay.getType() == Material.AIR) {
            // Fallback if no tool in hand
            toolDisplay = new ItemBuilder(Material.DIAMOND_HOE)
                .name("&bTool")
                .lore("&7Rolling...")
                .build();
        }
        inventory.setItem(toolSlot, toolDisplay);

        // Initial crystal slots
        for (int slot : crystalSlots) {
            if (slot < inventory.getSize()) {
                Material mat = crystalMaterials[0];
                ItemStack crystalItem = new ItemBuilder(mat)
                    .name(" ")
                    .build();
                inventory.setItem(slot, crystalItem);
            }
        }
    }

    /**
     * Starts the animation loop.
     * Runs every tick (not using configurable tick rate for smoother animation).
     */
    private void startAnimation() {
        animationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (animatingPlayer == null || !animatingPlayer.isOnline()) {
                    cancel();
                    cleanup();
                    return;
                }

                if (animationTick >= durationTicks) {
                    // Animation complete - do the roll
                    completeAnimation();
                    return;
                }

                // Update animation frame
                updateAnimation();
                animationTick++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick for smooth animation
    }

    /**
     * Updates the animation frame.
     * Crystal slots randomly cycle through materials each tick.
     */
    private void updateAnimation() {
        // Animate crystal slots - random materials each tick
        for (int slot : crystalSlots) {
            if (slot < inventory.getSize()) {
                Material mat = crystalMaterials[random.nextInt(crystalMaterials.length)];
                ItemStack crystalItem = new ItemBuilder(mat)
                    .name(" ")
                    .build();
                inventory.setItem(slot, crystalItem);
            }
        }

        // Play tick sound at interval
        if (animationTick % tickInterval == 0) {
            playSound(animatingPlayer, tickSound, tickVolume, 1.0f);
        }
    }

    /**
     * Completes the animation and reveals the result.
     */
    private void completeAnimation() {
        animationTask.cancel();
        animationComplete = true;

        // Execute the actual roll
        perkManager.roll(animatingPlayer, toolType).thenAccept(result -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.isSuccess()) {
                    finishAnimation(result.getPerk(), result.wasPityTriggered());
                } else {
                    // Roll failed
                    showFailure(result.getFailureReason());
                }
            });
        });
    }

    /**
     * Finishes the animation showing the result.
     * All crystal slots show category glass color.
     */
    private void finishAnimation(ActivePerk perk, boolean pityTriggered) {
        if (animatingPlayer == null || !animatingPlayer.isOnline()) {
            cleanup();
            return;
        }

        // Get category info from ConfigManager (unified category config)
        String categoryColor = messagesConfig.getCategoryColor(perk.getCategory());
        ConfigManager.CategoryConfig categoryConfig = configManager.getCategory(perk.getCategory());
        Material categoryGlass = categoryConfig.glassMaterial();

        // Set all crystal slots to category glass color
        ItemStack finalCrystal = new ItemBuilder(categoryGlass)
            .name(categoryColor + messagesConfig.getCategoryName(perk.getCategory()))
            .build();

        for (int slot : crystalSlots) {
            if (slot < inventory.getSize()) {
                inventory.setItem(slot, finalCrystal);
            }
        }

        // Play category-specific sound (from unified categories config)
        Sound categorySound = configManager.getCategorySound(perk.getCategory());
        playSound(animatingPlayer, categorySound, 1.0f, 1.0f);

        // Send chat message
        messagesConfig.send(animatingPlayer, "rolls.success",
            "perk", categoryColor + perk.getDisplayName(),
            "level", String.valueOf(perk.getLevel()),
            "tool", formatToolName(toolType));

        if (pityTriggered) {
            messagesConfig.send(animatingPlayer, "rolls.pity-triggered");
        }

        // Return to main GUI after delay so player can see the result
        // Store references before scheduling since cleanup() will null them
        final Player playerRef = animatingPlayer;
        final String toolRef = toolType;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (configManager.isDebug()) {
                plugin.getLogger().info("[DEBUG] Animation finished - reopening Main GUI for " +
                    (playerRef != null ? playerRef.getName() : "null") + " with toolType=" + toolRef);
            }

            // Clear animating state BEFORE opening main GUI
            // (openMainGui checks isAnimating and returns early if true)
            if (playerRef != null) {
                guiManager.clearAnimating(playerRef);
            }

            if (playerRef != null && playerRef.isOnline()) {
                guiManager.openMainGui(playerRef, toolRef);
            }
            cleanup();
        }, resultDisplayTicks);
    }

    /**
     * Shows a failure message.
     */
    private void showFailure(PerkManager.RollResult.FailureReason reason) {
        if (animatingPlayer == null || !animatingPlayer.isOnline()) {
            cleanup();
            return;
        }

        // Set crystal slots to red glass
        ItemStack failCrystal = new ItemBuilder(Material.RED_STAINED_GLASS_PANE)
            .name(" ")
            .build();

        for (int slot : crystalSlots) {
            if (slot < inventory.getSize()) {
                inventory.setItem(slot, failCrystal);
            }
        }

        // Play failure sound
        playSound(animatingPlayer, Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

        messagesConfig.send(animatingPlayer, "errors.roll-failed");

        // Return to main GUI after delay so player can see the result
        // Store references before scheduling since cleanup() will null them
        final Player playerRef = animatingPlayer;
        final String toolRef = toolType;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (configManager.isDebug()) {
                plugin.getLogger().info("[DEBUG] Animation failed - reopening Main GUI for " +
                    (playerRef != null ? playerRef.getName() : "null") + " with toolType=" + toolRef);
            }

            // Clear animating state BEFORE opening main GUI
            // (openMainGui checks isAnimating and returns early if true)
            if (playerRef != null) {
                guiManager.clearAnimating(playerRef);
            }

            if (playerRef != null && playerRef.isOnline()) {
                guiManager.openMainGui(playerRef, toolRef);
            }
            cleanup();
        }, resultDisplayTicks);
    }

    /**
     * Cleans up resources and clears animation state.
     */
    private void cleanup() {
        if (animationTask != null && !animationTask.isCancelled()) {
            animationTask.cancel();
        }

        if (animatingPlayer != null) {
            guiManager.clearAnimating(animatingPlayer);
        }

        animatingPlayer = null;
        animationTask = null;
        inventory = null;
    }

    /**
     * Called when the inventory is closed.
     */
    public void onClose() {
        if (!animationComplete && animationTask != null) {
            // Animation interrupted - still complete the roll
            animationTask.cancel();

            if (animatingPlayer != null && animatingPlayer.isOnline()) {
                PlayerData data = playerDataManager.getData(animatingPlayer.getUniqueId());
                if (data != null && data.hasRolls(1)) {
                    // Complete roll without visual
                    perkManager.roll(animatingPlayer, toolType).thenAccept(result -> {
                        if (result.isSuccess()) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                String categoryColor = messagesConfig.getCategoryColor(result.getPerk().getCategory());
                                messagesConfig.send(animatingPlayer, "rolls.success",
                                    "perk", categoryColor + result.getPerk().getDisplayName(),
                                    "level", String.valueOf(result.getPerk().getLevel()),
                                    "tool", formatToolName(toolType));
                            });
                        }
                    });
                }
            }
        }

        cleanup();
    }

    /**
     * Plays a sound to the player.
     */
    private void playSound(Player player, Sound sound, float volume, float pitch) {
        if (player != null && player.isOnline()) {
            try {
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception ignored) {
                // Ignore sound errors
            }
        }
    }

    /**
     * Formats a tool type for display.
     */
    private String formatToolName(String toolType) {
        if (toolType == null) return messagesConfig.get("fallback.unknown-tool", "Unknown");
        return configManager.getToolDisplayName(toolType);
    }

    /**
     * Checks if this GUI is currently animating.
     */
    public boolean isAnimating() {
        return !animationComplete && animationTask != null && !animationTask.isCancelled();
    }

    /**
     * Gets the animating player.
     */
    public Player getAnimatingPlayer() {
        return animatingPlayer;
    }
}
