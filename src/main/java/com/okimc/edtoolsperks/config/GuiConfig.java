package com.okimc.edtoolsperks.config;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Level;

/**
 * Handles loading and parsing of GUI layouts from guis.yml.
 * Provides structured access to GUI definitions, item configurations, and actions.
 *
 * <p>Supports three GUI types:</p>
 * <ul>
 *   <li>main-gui - Main perks menu</li>
 *   <li>perks-list-gui - Available perks catalog</li>
 *   <li>animation-gui - Roll animation display</li>
 * </ul>
 *
 * @author Snopeyy
 * @version 2.0.0
 */
public class GuiConfig {

    private static final String FILE_NAME = "guis.yml";

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    // Loaded GUI definitions
    private GuiDefinition mainGui;
    private GuiDefinition perksListGui;
    private GuiDefinition animationGui;

    /**
     * Creates a new GuiConfig instance.
     *
     * @param plugin The main plugin instance
     */
    public GuiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), FILE_NAME);
    }

    /**
     * Loads the GUI configuration file.
     */
    public void load() {
        if (!configFile.exists()) {
            plugin.saveResource(FILE_NAME, false);
        }

        config = YamlConfiguration.loadConfiguration(configFile);

        // Merge with defaults
        InputStream defaultStream = plugin.getResource(FILE_NAME);
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defaultConfig);
        }

        // Load GUI definitions
        loadGuiDefinitions();
    }

    /**
     * Reloads the GUI configuration.
     */
    public void reload() {
        mainGui = null;
        perksListGui = null;
        animationGui = null;
        load();
    }

    /**
     * Loads all GUI definitions from config.
     */
    private void loadGuiDefinitions() {
        try {
            mainGui = parseGuiDefinition("main-gui");
            perksListGui = parseGuiDefinition("perks-list-gui");
            animationGui = parseGuiDefinition("animation-gui");

            plugin.getLogger().info("GUI configurations loaded successfully.");

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load GUI configurations!", e);
        }
    }

    /**
     * Parses a single GUI definition from config.
     *
     * @param guiKey The config key for the GUI (e.g., "main-gui")
     * @return The parsed GuiDefinition or null
     */
    private GuiDefinition parseGuiDefinition(String guiKey) {
        ConfigurationSection guiSection = config.getConfigurationSection(guiKey);
        if (guiSection == null) {
            plugin.getLogger().warning("GUI section not found: " + guiKey);
            return new GuiDefinition(guiKey, guiKey, 54, new HashMap<>(), new HashMap<>());
        }

        String title = translateColors(guiSection.getString("title", guiKey));
        int size = guiSection.getInt("size", 54);

        // Ensure size is a multiple of 9
        if (size % 9 != 0) {
            size = ((size / 9) + 1) * 9;
        }
        if (size > 54) size = 54;
        if (size < 9) size = 9;

        // Parse settings (for perks-list-gui)
        Map<String, Object> settings = new HashMap<>();
        ConfigurationSection settingsSection = guiSection.getConfigurationSection("settings");
        if (settingsSection != null) {
            for (String key : settingsSection.getKeys(false)) {
                Object value = settingsSection.get(key);
                settings.put(key, value);
            }
        }

        // Read perk-slots from GUI root level (not settings) and parse ranges
        String perkSlotsStr = guiSection.getString("perk-slots");
        if (perkSlotsStr != null && !perkSlotsStr.isEmpty()) {
            // Store the parsed slots set directly in settings for efficient access
            Set<Integer> parsedPerkSlots = parseSlots(perkSlotsStr);
            settings.put("perk-slots-parsed", parsedPerkSlots);
        }

        // Parse perk-item-template (for perks-list-gui)
        ConfigurationSection templateSection = guiSection.getConfigurationSection("perk-item-template");
        if (templateSection != null) {
            Map<String, Object> template = new HashMap<>();
            template.put("name", templateSection.getString("name", "{category_color}{display_name}"));
            template.put("lore", templateSection.getStringList("lore"));
            template.put("level-format", templateSection.getString("level-format", "&8 - &7Nivel {level}: &a{boost}"));
            template.put("level-current-format", templateSection.getString("level-current-format", "&8 - &e&lNivel {level}: &a&l{boost} &7(Actual)"));
            template.put("equipped-text", templateSection.getString("equipped-text", "&a&l✓ EQUIPADO"));
            settings.put("perk-item-template", template);
        }

        // Parse items
        Map<String, GuiItem> items = new HashMap<>();
        ConfigurationSection itemsSection = guiSection.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemKey : itemsSection.getKeys(false)) {
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(itemKey);
                if (itemSection != null) {
                    GuiItem item = parseGuiItem(itemKey, itemSection);
                    if (item != null) {
                        items.put(itemKey, item);
                    }
                }
            }
        }

        return new GuiDefinition(guiKey, title, size, items, settings);
    }

    /**
     * Parses a single GUI item from config.
     *
     * @param itemKey The item's key
     * @param section The configuration section
     * @return The parsed GuiItem or null
     */
    private GuiItem parseGuiItem(String itemKey, ConfigurationSection section) {
        // Material can be a string or list (for animation)
        String materialStr = section.getString("material", "STONE");
        List<String> materials = section.getStringList("materials");
        if (materials.isEmpty() && materialStr != null) {
            materials = Collections.singletonList(materialStr);
        }

        String name = translateColors(section.getString("name", ""));
        List<String> lore = section.getStringList("lore").stream()
            .map(this::translateColors)
            .toList();

        // Parse slots (supports both string format "0-8,36-44" and list format [9, 17, 18])
        Set<Integer> slots = parseSlotsFromSection(section);

        // Parse actions
        Map<String, List<String>> actions = new HashMap<>();
        ConfigurationSection actionsSection = section.getConfigurationSection("actions");
        if (actionsSection != null) {
            for (String actionType : actionsSection.getKeys(false)) {
                List<String> actionList = actionsSection.getStringList(actionType);
                if (actionList.isEmpty()) {
                    String singleAction = actionsSection.getString(actionType);
                    if (singleAction != null) {
                        actionList = Collections.singletonList(singleAction);
                    }
                }
                actions.put(actionType.toLowerCase(), actionList);
            }
        }

        // Parse glow effect
        boolean glow = section.getBoolean("glow", false);

        // Parse custom model data
        int modelData = section.getInt("model-data", 0);

        return new GuiItem(itemKey, materials, name, lore, slots, actions, glow, modelData);
    }

    /**
     * Translates color codes including hex colors (&#RRGGBB format).
     *
     * @param text The text to translate
     * @return The translated text with color codes
     */
    private String translateColors(String text) {
        if (text == null) return "";

        // First translate hex colors (&#RRGGBB format)
        text = translateHexColors(text);

        // Then translate legacy color codes
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Translates hex color codes in &#RRGGBB format to Bukkit color format.
     *
     * @param text The text containing hex colors
     * @return The text with hex colors translated
     */
    private String translateHexColors(String text) {
        if (text == null || !text.contains("&#")) return text;

        // Pattern for &#RRGGBB format
        java.util.regex.Pattern hexPattern = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})");
        java.util.regex.Matcher matcher = hexPattern.matcher(text);

        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            // Convert to Bukkit's hex format: §x§R§R§G§G§B§B
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char c : hex.toCharArray()) {
                replacement.append("\u00A7").append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(result, replacement.toString());
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Parses slots from a config section, supporting both string and list formats.
     * Uses LinkedHashSet to preserve insertion order.
     *
     * <p>Supports:</p>
     * <ul>
     *   <li>String format: "0-8,36-44" or "9,17,18"</li>
     *   <li>List format: [9, 17, 18, 26, 27, 35]</li>
     * </ul>
     *
     * @param section The configuration section containing "slots"
     * @return Set of slot indices in order
     */
    private Set<Integer> parseSlotsFromSection(ConfigurationSection section) {
        Object slotsObj = section.get("slots");

        if (slotsObj instanceof String slotsStr) {
            // String format: "0-8,36-44" or "9,17,18"
            return parseSlots(slotsStr);
        } else if (slotsObj instanceof List<?> list) {
            // List format: [9, 17, 18, 26, 27, 35] - preserve order with LinkedHashSet
            Set<Integer> slots = new LinkedHashSet<>();
            for (Object o : list) {
                if (o instanceof Number) {
                    slots.add(((Number) o).intValue());
                }
            }
            return slots;
        }

        // Default to slot 0
        return new LinkedHashSet<>(Set.of(0));
    }

    /**
     * Parses a slots string into a set of integers.
     * Supports comma-separated values (e.g., "0,1,2,3") and ranges (e.g., "0-8").
     * Uses LinkedHashSet to preserve the order as specified in the config.
     *
     * @param slotsStr The slots string
     * @return Set of slot indices in order
     */
    private Set<Integer> parseSlots(String slotsStr) {
        // Use LinkedHashSet to preserve insertion order
        Set<Integer> slots = new LinkedHashSet<>();
        if (slotsStr == null || slotsStr.isEmpty()) {
            return slots;
        }

        String[] parts = slotsStr.split(",");
        for (String part : parts) {
            part = part.trim();

            if (part.contains("-")) {
                // Range format: "0-8"
                String[] rangeParts = part.split("-");
                if (rangeParts.length == 2) {
                    try {
                        int start = Integer.parseInt(rangeParts[0].trim());
                        int end = Integer.parseInt(rangeParts[1].trim());
                        for (int i = start; i <= end; i++) {
                            slots.add(i);
                        }
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                // Single slot
                try {
                    slots.add(Integer.parseInt(part));
                } catch (NumberFormatException ignored) {}
            }
        }

        return slots;
    }

    // ==================== Getters ====================

    /**
     * Gets the main GUI definition.
     *
     * @return The main GUI definition
     */
    public GuiDefinition getMainGui() {
        return mainGui;
    }

    /**
     * Gets the perks list GUI definition.
     *
     * @return The perks list GUI definition
     */
    public GuiDefinition getPerksListGui() {
        return perksListGui;
    }

    /**
     * Gets the animation GUI definition.
     *
     * @return The animation GUI definition
     */
    public GuiDefinition getAnimationGui() {
        return animationGui;
    }

    /**
     * Gets the raw FileConfiguration.
     *
     * @return The FileConfiguration
     */
    public FileConfiguration getConfig() {
        return config;
    }

    // ==================== Records ====================

    /**
     * Record representing a complete GUI definition.
     */
    public record GuiDefinition(
        String key,
        String title,
        int size,
        Map<String, GuiItem> items,
        Map<String, Object> settings
    ) {
        /**
         * Gets an item by its key.
         *
         * @param itemKey The item key
         * @return The GuiItem or null
         */
        public GuiItem getItem(String itemKey) {
            return items.get(itemKey);
        }

        /**
         * Gets a setting value.
         *
         * @param settingKey The setting key
         * @return The setting value or null
         */
        @SuppressWarnings("unchecked")
        public <T> T getSetting(String settingKey) {
            return (T) settings.get(settingKey);
        }

        /**
         * Gets a setting value with default.
         *
         * @param settingKey   The setting key
         * @param defaultValue The default value
         * @return The setting value or default
         */
        @SuppressWarnings("unchecked")
        public <T> T getSetting(String settingKey, T defaultValue) {
            Object value = settings.get(settingKey);
            return value != null ? (T) value : defaultValue;
        }

        /**
         * Gets the category order for perks list GUI.
         *
         * @return List of category keys in order
         */
        @SuppressWarnings("unchecked")
        public List<String> getCategoryOrder() {
            Object order = settings.get("category-order");
            if (order instanceof List) {
                return (List<String>) order;
            }
            return Collections.emptyList();
        }

        /**
         * Gets the perk slots for perks list GUI.
         * Supports range format like "10-16,19-25" parsed during load.
         * Returns slots in the order specified in config (uses LinkedHashSet).
         *
         * @return Set of slot indices for perks, in order
         */
        @SuppressWarnings("unchecked")
        public Set<Integer> getPerkSlots() {
            // First check for pre-parsed slots (with range support)
            Object parsedSlots = settings.get("perk-slots-parsed");
            if (parsedSlots instanceof Set) {
                return (Set<Integer>) parsedSlots;
            }

            // Fallback to legacy string parsing - use LinkedHashSet to preserve order
            Object slotsObj = settings.get("perk-slots");
            if (slotsObj instanceof String slotsStr) {
                Set<Integer> slots = new LinkedHashSet<>();
                for (String part : slotsStr.split(",")) {
                    part = part.trim();
                    if (part.contains("-")) {
                        // Range format: "10-16"
                        String[] rangeParts = part.split("-");
                        if (rangeParts.length == 2) {
                            try {
                                int start = Integer.parseInt(rangeParts[0].trim());
                                int end = Integer.parseInt(rangeParts[1].trim());
                                for (int i = start; i <= end; i++) {
                                    slots.add(i);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    } else {
                        try {
                            slots.add(Integer.parseInt(part));
                        } catch (NumberFormatException ignored) {}
                    }
                }
                return slots;
            }
            return new LinkedHashSet<>();
        }

        /**
         * Gets all items that should be placed in specific slots.
         *
         * @return Map of slot index to list of items for that slot
         */
        public Map<Integer, List<GuiItem>> getSlotMappings() {
            Map<Integer, List<GuiItem>> slotMap = new HashMap<>();

            for (GuiItem item : items.values()) {
                for (int slot : item.slots()) {
                    slotMap.computeIfAbsent(slot, k -> new ArrayList<>()).add(item);
                }
            }

            return slotMap;
        }

        /**
         * Gets the perk item template configuration.
         *
         * @return Map containing template settings, or null if not configured
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> getPerkItemTemplate() {
            Object template = settings.get("perk-item-template");
            if (template instanceof Map) {
                return (Map<String, Object>) template;
            }
            return null;
        }
    }

    /**
     * Record representing a single GUI item definition.
     */
    public record GuiItem(
        String key,
        List<String> materials,
        String name,
        List<String> lore,
        Set<Integer> slots,
        Map<String, List<String>> actions,
        boolean glow,
        int modelData
    ) {
        /**
         * Gets the primary material string.
         *
         * @return The first material string
         */
        public String getMaterialString() {
            return materials.isEmpty() ? "STONE" : materials.get(0);
        }

        /**
         * Checks if this item has a custom texture.
         *
         * @return true if material starts with "texture-"
         */
        public boolean hasCustomTexture() {
            String mat = getMaterialString();
            return mat != null && mat.toLowerCase().startsWith("texture-");
        }

        /**
         * Gets the texture value for custom heads.
         *
         * @return The base64 texture value or null
         */
        public String getTextureValue() {
            if (!hasCustomTexture()) return null;
            return getMaterialString().substring("texture-".length());
        }

        /**
         * Gets the Material for this item.
         *
         * @return The Material or STONE as fallback
         */
        public Material getMaterial() {
            String matStr = getMaterialString();

            // Check for placeholder/dynamic materials
            if (matStr.startsWith("{") && matStr.endsWith("}")) {
                return null; // Dynamic, will be set at runtime
            }

            if (hasCustomTexture()) {
                return Material.PLAYER_HEAD;
            }

            try {
                return Material.valueOf(matStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Material.STONE;
            }
        }

        /**
         * Checks if this item is dynamic (has placeholders in material).
         *
         * @return true if material contains placeholders
         */
        public boolean isDynamic() {
            String mat = getMaterialString();
            return mat.contains("{") && mat.contains("}");
        }

        /**
         * Gets actions for a specific click type.
         *
         * @param clickType The click type (e.g., "click", "left-click", "right-click")
         * @return List of actions or empty list
         */
        public List<String> getActions(String clickType) {
            // Try specific click type first
            List<String> specific = actions.get(clickType.toLowerCase());
            if (specific != null && !specific.isEmpty()) {
                return specific;
            }

            // Fall back to generic "click"
            List<String> generic = actions.get("click");
            return generic != null ? generic : Collections.emptyList();
        }

        /**
         * Checks if this item has any actions.
         *
         * @return true if actions are defined
         */
        public boolean hasActions() {
            return !actions.isEmpty() &&
                actions.values().stream()
                    .anyMatch(list -> !list.isEmpty() &&
                        !list.stream().allMatch(a -> a.equalsIgnoreCase("none")));
        }

        /**
         * Replaces placeholders in name and lore.
         *
         * @param replacements Key-value replacement pairs
         * @return New GuiItem with placeholders replaced
         */
        public GuiItem withReplacements(Map<String, String> replacements) {
            String newName = name;
            List<String> newLore = new ArrayList<>(lore);
            List<String> newMaterials = new ArrayList<>(materials);

            for (Map.Entry<String, String> entry : replacements.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = entry.getValue();

                newName = newName.replace(placeholder, value);

                for (int i = 0; i < newLore.size(); i++) {
                    newLore.set(i, newLore.get(i).replace(placeholder, value));
                }

                for (int i = 0; i < newMaterials.size(); i++) {
                    newMaterials.set(i, newMaterials.get(i).replace(placeholder, value));
                }
            }

            return new GuiItem(key, newMaterials, newName, newLore, slots, actions, glow, modelData);
        }

        /**
         * Checks if this item has a glow effect.
         *
         * @return true if glow is enabled
         */
        public boolean hasGlow() {
            return glow;
        }

        /**
         * Checks if this item has custom model data.
         *
         * @return true if model data is set (non-zero)
         */
        public boolean hasModelData() {
            return modelData > 0;
        }
    }
}
