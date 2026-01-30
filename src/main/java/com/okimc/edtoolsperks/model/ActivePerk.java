package com.okimc.edtoolsperks.model;

import com.okimc.edtoolsperks.config.PerksConfig.PerkDefinition;
import com.okimc.edtoolsperks.config.PerksConfig.PerkLevel;

import java.util.Map;
import java.util.Objects;

/**
 * Represents an active perk that a player has equipped on a specific tool type.
 * This is the runtime representation of a perk assignment stored in the database.
 *
 * <p>Each player can have at most one ActivePerk per tool type. The perk provides
 * boosts to various currencies/stats when using that tool.</p>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class ActivePerk {

    private final String perkKey;
    private final String toolType;
    private int level;

    // Cached reference to the perk definition (set after loading from config)
    private transient PerkDefinition definition;

    /**
     * Creates a new ActivePerk instance.
     *
     * @param perkKey  The unique key of the perk (from perks.yml)
     * @param toolType The tool type this perk is active on (e.g., "hoe", "pickaxe")
     * @param level    The current level of the perk (1-5 typically)
     */
    public ActivePerk(String perkKey, String toolType, int level) {
        this.perkKey = Objects.requireNonNull(perkKey, "perkKey cannot be null").toLowerCase();
        this.toolType = Objects.requireNonNull(toolType, "toolType cannot be null").toLowerCase();
        this.level = Math.max(1, level);
    }

    /**
     * Creates an ActivePerk with a linked PerkDefinition.
     *
     * @param definition The perk definition from config
     * @param toolType   The tool type
     * @param level      The current level
     */
    public ActivePerk(PerkDefinition definition, String toolType, int level) {
        this(definition.key(), toolType, level);
        this.definition = definition;
    }

    /**
     * Gets the unique key of this perk.
     *
     * @return The perk key (lowercase)
     */
    public String getPerkKey() {
        return perkKey;
    }

    /**
     * Gets the tool type this perk is active on.
     *
     * @return The tool type (lowercase)
     */
    public String getToolType() {
        return toolType;
    }

    /**
     * Gets the current level of this perk.
     *
     * @return The level (1 or higher)
     */
    public int getLevel() {
        return level;
    }

    /**
     * Sets the level of this perk.
     *
     * @param level The new level (will be clamped to 1 minimum)
     */
    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    /**
     * Upgrades the perk by one level if possible.
     *
     * @return true if upgraded, false if already at max level
     */
    public boolean upgrade() {
        if (definition != null && level >= definition.getMaxLevel()) {
            return false;
        }
        level++;
        return true;
    }

    /**
     * Checks if this perk can be upgraded.
     *
     * @return true if can upgrade
     */
    public boolean canUpgrade() {
        if (definition == null) return true; // Assume yes if no definition linked
        return level < definition.getMaxLevel();
    }

    /**
     * Gets the linked PerkDefinition.
     *
     * @return The definition or null if not linked
     */
    public PerkDefinition getDefinition() {
        return definition;
    }

    /**
     * Links this ActivePerk to a PerkDefinition.
     * Should be called after loading from database to restore the reference.
     *
     * @param definition The perk definition from config
     */
    public void linkDefinition(PerkDefinition definition) {
        if (definition != null && definition.key().equalsIgnoreCase(this.perkKey)) {
            this.definition = definition;
        }
    }

    /**
     * Checks if this perk has a linked definition.
     *
     * @return true if definition is linked
     */
    public boolean hasDefinition() {
        return definition != null;
    }

    /**
     * Gets the display name of this perk.
     *
     * @return The display name or the key if no definition
     */
    public String getDisplayName() {
        return definition != null ? definition.displayName() : perkKey;
    }

    /**
     * Gets the category of this perk.
     *
     * @return The category key or "unknown" if no definition
     */
    public String getCategory() {
        return definition != null ? definition.category() : "unknown";
    }

    /**
     * Gets the current level's boost data.
     *
     * @return The PerkLevel for current level or null
     */
    public PerkLevel getCurrentLevelData() {
        if (definition == null) return null;
        return definition.getLevel(level);
    }

    /**
     * Gets a map of boost types to amounts for the current level.
     *
     * @return Map of boost type to amount, or empty map
     */
    public Map<String, Double> getBoostMap() {
        PerkLevel levelData = getCurrentLevelData();
        if (levelData == null) return Map.of();
        return levelData.getBoostMap();
    }

    /**
     * Gets the boost amount for a specific type at current level.
     *
     * @param boostType The boost type (e.g., "money", "orbs")
     * @return The boost amount or 0 if not found
     */
    public double getBoostAmount(String boostType) {
        return getBoostMap().getOrDefault(boostType.toLowerCase(), 0.0);
    }

    /**
     * Calculates the multiplier for EdTools booster API.
     * Converts percentage boost to decimal multiplier.
     *
     * @param boostType The boost type
     * @return The multiplier (e.g., 10% = 0.10)
     */
    public double getBoostMultiplier(String boostType) {
        return getBoostAmount(boostType) / 100.0;
    }

    /**
     * Gets a formatted string representation for display.
     *
     * @return Formatted string like "Coin Perk Lvl. 3"
     */
    public String getFormattedDisplay() {
        return getDisplayName() + " Lvl. " + level;
    }

    /**
     * Creates a unique booster ID for EdTools API.
     *
     * @param playerUuid The player's UUID string
     * @param boostType  The boost type
     * @return A unique booster ID
     */
    public String generateBoosterId(String playerUuid, String boostType) {
        return "edtoolsperks-" + playerUuid + "-" + toolType + "-" + boostType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActivePerk that = (ActivePerk) o;
        return perkKey.equals(that.perkKey) && toolType.equals(that.toolType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(perkKey, toolType);
    }

    @Override
    public String toString() {
        return "ActivePerk{" +
            "perkKey='" + perkKey + '\'' +
            ", toolType='" + toolType + '\'' +
            ", level=" + level +
            '}';
    }

    /**
     * Creates a copy of this ActivePerk.
     *
     * @return A new ActivePerk with the same data
     */
    public ActivePerk copy() {
        ActivePerk copy = new ActivePerk(perkKey, toolType, level);
        copy.definition = this.definition;
        return copy;
    }
}
