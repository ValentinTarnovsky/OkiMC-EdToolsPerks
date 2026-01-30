package com.okimc.edtoolsperks.utils;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for creating custom player heads with base64 textures.
 *
 * <p>Supports creating heads from:</p>
 * <ul>
 *   <li>Base64 texture values (from minecraft-heads.com, etc.)</li>
 *   <li>Player names</li>
 *   <li>UUIDs</li>
 * </ul>
 *
 * <p>Includes caching to avoid repeated texture applications.</p>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public final class SkullUtils {

    // Cache for created skulls to avoid repeated reflection/profile creation
    private static final Map<String, ItemStack> TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 100;

    private SkullUtils() {
        // Utility class - no instantiation
    }

    /**
     * Creates a custom head with a base64 texture.
     *
     * @param base64Texture The base64 texture value
     * @return The custom head ItemStack
     */
    public static ItemStack createCustomHead(String base64Texture) {
        if (base64Texture == null || base64Texture.isEmpty()) {
            return new ItemStack(Material.PLAYER_HEAD);
        }

        // Check cache
        ItemStack cached = TEXTURE_CACHE.get(base64Texture);
        if (cached != null) {
            return cached.clone();
        }

        // Create new head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta == null) {
            return head;
        }

        try {
            // Use Paper's PlayerProfile API
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", base64Texture));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);

            // Cache the result (with size limit)
            if (TEXTURE_CACHE.size() < MAX_CACHE_SIZE) {
                TEXTURE_CACHE.put(base64Texture, head.clone());
            }

        } catch (Exception e) {
            // Fallback to regular head if texture application fails
            return new ItemStack(Material.PLAYER_HEAD);
        }

        return head;
    }

    /**
     * Creates a player head for a specific player name.
     *
     * @param playerName The player name
     * @return The player head ItemStack
     */
    public static ItemStack createPlayerHead(String playerName) {
        if (playerName == null || playerName.isEmpty()) {
            return new ItemStack(Material.PLAYER_HEAD);
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            meta.setOwner(playerName);
            head.setItemMeta(meta);
        }

        return head;
    }

    /**
     * Creates a player head for a specific UUID.
     *
     * @param uuid The player UUID
     * @return The player head ItemStack
     */
    public static ItemStack createPlayerHead(UUID uuid) {
        if (uuid == null) {
            return new ItemStack(Material.PLAYER_HEAD);
        }

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            head.setItemMeta(meta);
        }

        return head;
    }

    /**
     * Applies a base64 texture to an existing skull ItemStack.
     *
     * @param head          The head ItemStack to modify
     * @param base64Texture The base64 texture value
     * @return The modified head (same instance)
     */
    public static ItemStack applyTexture(ItemStack head, String base64Texture) {
        if (head == null || head.getType() != Material.PLAYER_HEAD) {
            return head;
        }

        if (base64Texture == null || base64Texture.isEmpty()) {
            return head;
        }

        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) {
            return head;
        }

        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", base64Texture));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
        } catch (Exception ignored) {
            // Keep original head if texture fails
        }

        return head;
    }

    /**
     * Checks if a string looks like a base64 texture value.
     *
     * @param value The string to check
     * @return true if it appears to be a base64 texture
     */
    public static boolean isBase64Texture(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }

        // Base64 textures are typically long strings starting with eyJ
        // (which is the base64 encoding of '{"')
        return value.length() > 50 && value.startsWith("eyJ");
    }

    /**
     * Parses a material string that might be a texture reference.
     * Format: "texture-BASE64VALUE" or just material name.
     *
     * @param materialString The material string
     * @return The ItemStack (custom head if texture, or material)
     */
    public static ItemStack parseSkullOrMaterial(String materialString) {
        if (materialString == null || materialString.isEmpty()) {
            return new ItemStack(Material.STONE);
        }

        // Check for texture prefix
        String lower = materialString.toLowerCase();
        if (lower.startsWith("texture-")) {
            String texture = materialString.substring("texture-".length());
            return createCustomHead(texture);
        }

        // Check for base64 pattern
        if (isBase64Texture(materialString)) {
            return createCustomHead(materialString);
        }

        // Parse as regular material
        try {
            Material material = Material.valueOf(materialString.toUpperCase());
            return new ItemStack(material);
        } catch (IllegalArgumentException e) {
            return new ItemStack(Material.STONE);
        }
    }

    /**
     * Clears the texture cache.
     */
    public static void clearCache() {
        TEXTURE_CACHE.clear();
    }

    /**
     * Gets the current cache size.
     *
     * @return The number of cached textures
     */
    public static int getCacheSize() {
        return TEXTURE_CACHE.size();
    }

    // ==================== Common Textures ====================

    /**
     * Common texture constants for frequently used heads.
     */
    public static final class Textures {

        // Arrows
        public static final String ARROW_LEFT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmQ2OWUwNmU1ZGFkZmQ4NGU1ZjNkMWMyMTA2M2YyNTUzYjJmYTk0NWVlMWQ0ZDcxNTJmZGM1NDI1YmMxMmE5In19fQ==";
        public static final String ARROW_RIGHT = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTliZjMyOTJlMTI2YTEwNWI1NGViYTcxM2FhMWIxNTJkNTQxYTFkODkzODgyOWM1NjM2NGQxNzhlZDIyYmYifX19";
        public static final String ARROW_UP = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzA0MGZlODM2YTZjMmZiZDJjN2E5YzhlYzZiZTUxNzRmZGRmMWFjMjBmNTVlMzY2MTU2ZmE1ZjcxMmUxMCJ9fX0=";
        public static final String ARROW_DOWN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzQzNzM0NmQ4YmRhNzhkNTI1ZDE5ZjU0MGE5NWU0ZTc5ZGFlZTZiYzk2MmRlNzZmYmU5NjQxMmRmZWQyMDU4In19fQ==";

        // Symbols
        public static final String QUESTION_MARK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmM4ZWExZjUxZjI1M2ZmNTE0MmNhMTFhZTQ1MTkzYTRhZDhjMWMxNGE4NjE3OTEzOTVkYzY0YjM1YjM0YSJ9fX0=";
        public static final String EXCLAMATION_MARK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjYzMzM4ZGE4NzFjYzY0ZWFhZjI2NGY5MjFhZjkxZTJhNjdlMDMyMzJhOWJmZWI2Y2E0OWY4YjE0YjRiIn19fQ==";
        public static final String CHECK_MARK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTkyZTMxZmZiNTljOTBhYjA4ZmM5ZGMxZmUyNjgwMjAzNWEzYTQ3YzQyZmVlNjM0MjNiY2RiNDI2MmVjYjliNiJ9fX0=";
        public static final String X_MARK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmViNTg4YjIxYTZmOThhZDFmZjRlMDg1YzFiOGQ5NDdlNWIwZWZmOGRiNWQyMzY5YjhlMjdmNmM5YmY0NCJ9fX0=";

        // Colors
        public static final String DICE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTk3OGQ0MzY0YjY3OTI0ZGI0YTM5ZjU1OGRjNDI1NDYwNWM4NjE2NmNiOTk0ZjMzMzUxYjVhM2VlOTZhMDgwIn19fQ==";

        private Textures() {
        }
    }
}
