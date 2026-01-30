package com.okimc.edtoolsperks.model;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the runtime data for a player in the EdToolsPerks system.
 * This class holds all perk-related information for a single player.
 *
 * <p>Data stored:</p>
 * <ul>
 *   <li>UUID - Player's unique identifier</li>
 *   <li>Rolls - Available roll attempts</li>
 *   <li>Pity Counter - Rolls since last legendary (for pity system)</li>
 *   <li>Animations Enabled - Whether to show roll animations</li>
 *   <li>Active Perks - Map of tool type to ActivePerk</li>
 * </ul>
 *
 * <p>Thread-safe: Uses ConcurrentHashMap for perks storage.</p>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class PlayerData {

    private final UUID uuid;
    private int rolls;
    private int pityCount;
    private boolean animationsEnabled;

    // Map of tool type (lowercase) -> ActivePerk
    private final Map<String, ActivePerk> activePerks;

    // Dirty flag for tracking unsaved changes
    private volatile boolean dirty;

    /**
     * Creates a new PlayerData instance with default values.
     *
     * @param uuid The player's UUID
     */
    public PlayerData(UUID uuid) {
        this.uuid = Objects.requireNonNull(uuid, "UUID cannot be null");
        this.rolls = 0;
        this.pityCount = 0;
        this.animationsEnabled = true;
        this.activePerks = new ConcurrentHashMap<>();
        this.dirty = false;
    }

    /**
     * Creates a new PlayerData instance with specified values.
     *
     * @param uuid              The player's UUID
     * @param rolls             Available roll attempts
     * @param pityCount         Current pity counter
     * @param animationsEnabled Whether animations are enabled
     */
    public PlayerData(UUID uuid, int rolls, int pityCount, boolean animationsEnabled) {
        this.uuid = Objects.requireNonNull(uuid, "UUID cannot be null");
        this.rolls = Math.max(0, rolls);
        this.pityCount = Math.max(0, pityCount);
        this.animationsEnabled = animationsEnabled;
        this.activePerks = new ConcurrentHashMap<>();
        this.dirty = false;
    }

    // ==================== UUID ====================

    /**
     * Gets the player's UUID.
     *
     * @return The UUID
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the player's UUID as a string.
     *
     * @return The UUID string
     */
    public String getUuidString() {
        return uuid.toString();
    }

    // ==================== Rolls ====================

    /**
     * Gets the number of available rolls.
     *
     * @return The roll count
     */
    public int getRolls() {
        return rolls;
    }

    /**
     * Sets the number of available rolls.
     *
     * @param rolls The new roll count (clamped to 0 minimum)
     */
    public void setRolls(int rolls) {
        this.rolls = Math.max(0, rolls);
        this.dirty = true;
    }

    /**
     * Adds rolls to the player.
     *
     * @param amount The amount to add
     */
    public void addRolls(int amount) {
        this.rolls = Math.max(0, this.rolls + amount);
        this.dirty = true;
    }

    /**
     * Removes rolls from the player.
     *
     * @param amount The amount to remove
     * @return true if had enough rolls, false otherwise
     */
    public boolean removeRolls(int amount) {
        if (this.rolls < amount) {
            return false;
        }
        this.rolls -= amount;
        this.dirty = true;
        return true;
    }

    /**
     * Checks if the player has at least the specified number of rolls.
     *
     * @param amount The amount to check
     * @return true if has enough rolls
     */
    public boolean hasRolls(int amount) {
        return this.rolls >= amount;
    }

    /**
     * Consumes one roll if available.
     *
     * @return true if a roll was consumed, false if no rolls available
     */
    public boolean consumeRoll() {
        return removeRolls(1);
    }

    // ==================== Pity System ====================

    /**
     * Gets the current pity counter.
     *
     * @return The pity count (rolls since last legendary)
     */
    public int getPityCount() {
        return pityCount;
    }

    /**
     * Sets the pity counter.
     *
     * @param pityCount The new pity count
     */
    public void setPityCount(int pityCount) {
        this.pityCount = Math.max(0, pityCount);
        this.dirty = true;
    }

    /**
     * Increments the pity counter by one.
     */
    public void incrementPity() {
        this.pityCount++;
        this.dirty = true;
    }

    /**
     * Resets the pity counter to zero.
     * Called when player receives a legendary perk.
     */
    public void resetPity() {
        this.pityCount = 0;
        this.dirty = true;
    }

    /**
     * Checks if the player has reached the pity threshold.
     *
     * @param threshold The pity threshold from config
     * @return true if pity count >= threshold
     */
    public boolean hasPityReached(int threshold) {
        return this.pityCount >= threshold;
    }

    // ==================== Animations ====================

    /**
     * Checks if animations are enabled for this player.
     *
     * @return true if animations are enabled
     */
    public boolean isAnimationsEnabled() {
        return animationsEnabled;
    }

    /**
     * Sets whether animations are enabled.
     *
     * @param enabled true to enable animations
     */
    public void setAnimationsEnabled(boolean enabled) {
        this.animationsEnabled = enabled;
        this.dirty = true;
    }

    /**
     * Toggles the animations setting.
     *
     * @return The new state (true if now enabled)
     */
    public boolean toggleAnimations() {
        this.animationsEnabled = !this.animationsEnabled;
        this.dirty = true;
        return this.animationsEnabled;
    }

    // ==================== Active Perks ====================

    /**
     * Gets the active perk for a specific tool type.
     *
     * @param toolType The tool type (case-insensitive)
     * @return The ActivePerk or null if none
     */
    public ActivePerk getPerk(String toolType) {
        return activePerks.get(toolType.toLowerCase());
    }

    /**
     * Checks if the player has an active perk for a tool type.
     *
     * @param toolType The tool type
     * @return true if has a perk for that tool
     */
    public boolean hasPerk(String toolType) {
        return activePerks.containsKey(toolType.toLowerCase());
    }

    /**
     * Sets or replaces the active perk for a tool type.
     *
     * @param perk The ActivePerk to set
     * @return The previous perk if any, or null
     */
    public ActivePerk setPerk(ActivePerk perk) {
        if (perk == null) return null;
        this.dirty = true;
        return activePerks.put(perk.getToolType().toLowerCase(), perk);
    }

    /**
     * Sets or replaces the active perk for a specific tool type.
     *
     * @param toolType The tool type
     * @param perk     The ActivePerk to set
     * @return The previous perk if any, or null
     */
    public ActivePerk setPerk(String toolType, ActivePerk perk) {
        if (perk == null) return null;
        this.dirty = true;
        return activePerks.put(toolType.toLowerCase(), perk);
    }

    /**
     * Removes the active perk for a tool type.
     *
     * @param toolType The tool type
     * @return The removed perk or null if none
     */
    public ActivePerk removePerk(String toolType) {
        ActivePerk removed = activePerks.remove(toolType.toLowerCase());
        if (removed != null) {
            this.dirty = true;
        }
        return removed;
    }

    /**
     * Gets all active perks.
     *
     * @return Unmodifiable map of tool type to ActivePerk
     */
    public Map<String, ActivePerk> getAllPerks() {
        return Collections.unmodifiableMap(activePerks);
    }

    /**
     * Gets the number of active perks.
     *
     * @return The count of active perks
     */
    public int getPerkCount() {
        return activePerks.size();
    }

    /**
     * Clears all active perks.
     */
    public void clearPerks() {
        if (!activePerks.isEmpty()) {
            activePerks.clear();
            this.dirty = true;
        }
    }

    // ==================== Dirty Tracking ====================

    /**
     * Checks if this data has unsaved changes.
     *
     * @return true if there are unsaved changes
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Marks the data as clean (saved).
     */
    public void markClean() {
        this.dirty = false;
    }

    /**
     * Marks the data as dirty (needs saving).
     */
    public void markDirty() {
        this.dirty = true;
    }

    // ==================== Utility ====================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlayerData that = (PlayerData) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }

    @Override
    public String toString() {
        return "PlayerData{" +
            "uuid=" + uuid +
            ", rolls=" + rolls +
            ", pityCount=" + pityCount +
            ", animationsEnabled=" + animationsEnabled +
            ", perks=" + activePerks.size() +
            ", dirty=" + dirty +
            '}';
    }

    /**
     * Creates a builder for constructing PlayerData instances.
     *
     * @param uuid The player's UUID
     * @return A new builder instance
     */
    public static Builder builder(UUID uuid) {
        return new Builder(uuid);
    }

    /**
     * Builder class for constructing PlayerData instances.
     */
    public static class Builder {
        private final UUID uuid;
        private int rolls = 0;
        private int pityCount = 0;
        private boolean animationsEnabled = true;

        public Builder(UUID uuid) {
            this.uuid = uuid;
        }

        public Builder rolls(int rolls) {
            this.rolls = rolls;
            return this;
        }

        public Builder pityCount(int pityCount) {
            this.pityCount = pityCount;
            return this;
        }

        public Builder animationsEnabled(boolean enabled) {
            this.animationsEnabled = enabled;
            return this;
        }

        public PlayerData build() {
            return new PlayerData(uuid, rolls, pityCount, animationsEnabled);
        }
    }
}
