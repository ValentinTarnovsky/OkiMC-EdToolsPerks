package com.okimc.edtoolsperks.config;

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
import java.util.stream.Collectors;

/**
 * Handles loading and parsing of perk definitions from perks.yml.
 * Provides access to perk data, categories, and weighted selection pools.
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class PerksConfig {

    private static final String FILE_NAME = "perks.yml";

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    // Reference to ConfigManager for category lookups
    private ConfigManager configManager;

    // All loaded perk definitions indexed by key
    private final Map<String, PerkDefinition> perksByKey = new HashMap<>();

    // Perks grouped by tool type for quick lookup
    private final Map<String, List<PerkDefinition>> perksByTool = new HashMap<>();

    // Perks grouped by category
    private final Map<String, List<PerkDefinition>> perksByCategory = new HashMap<>();

    /**
     * Creates a new PerksConfig instance.
     *
     * @param plugin The main plugin instance
     */
    public PerksConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), FILE_NAME);
    }

    /**
     * Sets the ConfigManager reference for category lookups.
     * Called after ConfigManager is fully initialized.
     *
     * @param configManager The ConfigManager instance
     */
    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Loads the perks configuration file.
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

        // NOTE: Categories are now loaded from config.yml via ConfigManager

        // Load perks
        loadPerks();
    }

    /**
     * Reloads the perks configuration.
     */
    public void reload() {
        perksByKey.clear();
        perksByTool.clear();
        perksByCategory.clear();
        load();
    }

    // NOTE: loadCategoryConfigs removed - categories are now in config.yml

    /**
     * Loads all perk definitions from the config.
     */
    private void loadPerks() {
        ConfigurationSection perksSection = config.getConfigurationSection("perks");
        if (perksSection == null) {
            plugin.getLogger().warning("No perks section found in " + FILE_NAME);
            return;
        }

        int loaded = 0;
        for (String perkKey : perksSection.getKeys(false)) {
            ConfigurationSection perkSection = perksSection.getConfigurationSection(perkKey);
            if (perkSection == null) continue;

            try {
                PerkDefinition perk = parsePerk(perkKey, perkSection);
                if (perk != null) {
                    // Index by key
                    perksByKey.put(perkKey.toLowerCase(), perk);

                    // Index by tool type
                    perksByTool.computeIfAbsent(perk.tool().toLowerCase(), k -> new ArrayList<>())
                        .add(perk);

                    // Index by category
                    perksByCategory.computeIfAbsent(perk.category().toLowerCase(), k -> new ArrayList<>())
                        .add(perk);

                    loaded++;
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load perk: " + perkKey, e);
            }
        }

        plugin.getLogger().info("Loaded " + loaded + " perk definitions.");
    }

    /**
     * Parses a single perk definition from config.
     *
     * @param key     The perk's unique key
     * @param section The configuration section
     * @return The parsed PerkDefinition or null
     */
    private PerkDefinition parsePerk(String key, ConfigurationSection section) {
        String category = section.getString("category", "verde").toLowerCase();
        String displayName = section.getString("display-name", key);
        String description = section.getString("description", "");
        String tool = section.getString("tool", "hoe").toLowerCase();
        double chance = section.getDouble("chance", 1.0);
        String materialStr = section.getString("material", "ENCHANTED_BOOK");
        List<String> commands = section.getStringList("cmds");

        // Parse levels
        ConfigurationSection levelsSection = section.getConfigurationSection("levels");
        Map<Integer, PerkLevel> levels = new HashMap<>();

        if (levelsSection != null) {
            for (String levelKey : levelsSection.getKeys(false)) {
                try {
                    int levelNum = Integer.parseInt(levelKey);
                    ConfigurationSection levelSection = levelsSection.getConfigurationSection(levelKey);

                    if (levelSection != null) {
                        PerkLevel perkLevel = parsePerkLevel(levelSection);
                        levels.put(levelNum, perkLevel);
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid level key for perk " + key + ": " + levelKey);
                }
            }
        }

        // If no levels defined, create a default level 1
        if (levels.isEmpty()) {
            levels.put(1, new PerkLevel(new String[]{"money"}, new double[]{10.0}));
        }

        return new PerkDefinition(
            key.toLowerCase(),
            displayName,
            description,
            tool,
            category,
            chance,
            materialStr,
            commands,
            levels
        );
    }

    /**
     * Parses a perk level definition.
     *
     * @param section The level configuration section
     * @return The parsed PerkLevel
     */
    private PerkLevel parsePerkLevel(ConfigurationSection section) {
        // YAML uses "boost-types" (plural) as a list: [coins, orbs]
        List<String> boostTypesList = section.getStringList("boost-types");
        if (boostTypesList.isEmpty()) {
            // Fallback to singular "boost-type" for backwards compatibility
            String singleType = section.getString("boost-type", "money");
            boostTypesList = List.of(singleType.split(","));
        }

        // Convert to array and normalize
        String[] boostTypes = new String[boostTypesList.size()];
        for (int i = 0; i < boostTypesList.size(); i++) {
            boostTypes[i] = boostTypesList.get(i).trim().toLowerCase();
        }

        // YAML uses "boost-amounts" (plural) as a list: [5, 3]
        List<?> boostAmountsList = section.getList("boost-amounts");
        double[] boostAmounts;

        if (boostAmountsList != null && !boostAmountsList.isEmpty()) {
            boostAmounts = new double[boostAmountsList.size()];
            for (int i = 0; i < boostAmountsList.size(); i++) {
                Object val = boostAmountsList.get(i);
                if (val instanceof Number) {
                    boostAmounts[i] = ((Number) val).doubleValue();
                } else if (val instanceof String) {
                    try {
                        boostAmounts[i] = Double.parseDouble((String) val);
                    } catch (NumberFormatException e) {
                        boostAmounts[i] = 0;
                    }
                } else {
                    boostAmounts[i] = 0;
                }
            }
        } else {
            // Fallback to singular "boost-amount" for backwards compatibility
            Object boostAmountObj = section.get("boost-amount", 10);
            if (boostAmountObj instanceof Number) {
                boostAmounts = new double[]{((Number) boostAmountObj).doubleValue()};
            } else if (boostAmountObj instanceof String) {
                String[] parts = ((String) boostAmountObj).split(",");
                boostAmounts = new double[parts.length];
                for (int i = 0; i < parts.length; i++) {
                    try {
                        boostAmounts[i] = Double.parseDouble(parts[i].trim());
                    } catch (NumberFormatException e) {
                        boostAmounts[i] = 0;
                    }
                }
            } else {
                boostAmounts = new double[]{10.0};
            }
        }

        // Ensure arrays are same length
        if (boostAmounts.length < boostTypes.length) {
            double[] newAmounts = new double[boostTypes.length];
            System.arraycopy(boostAmounts, 0, newAmounts, 0, boostAmounts.length);
            for (int i = boostAmounts.length; i < boostTypes.length; i++) {
                newAmounts[i] = boostAmounts[boostAmounts.length - 1];
            }
            boostAmounts = newAmounts;
        }

        return new PerkLevel(boostTypes, boostAmounts);
    }

    // ==================== Getters ====================

    /**
     * Gets a perk definition by its key.
     *
     * @param key The perk key (case-insensitive)
     * @return The PerkDefinition or null if not found
     */
    public PerkDefinition getPerk(String key) {
        return perksByKey.get(key.toLowerCase());
    }

    /**
     * Gets all perks for a specific tool type.
     *
     * @param toolType The tool type (e.g., "hoe", "pickaxe")
     * @return List of perks for that tool, or empty list
     */
    public List<PerkDefinition> getPerksForTool(String toolType) {
        return perksByTool.getOrDefault(toolType.toLowerCase(), Collections.emptyList());
    }

    /**
     * Gets all perks in a specific category.
     *
     * @param category The category key
     * @return List of perks in that category, or empty list
     */
    public List<PerkDefinition> getPerksInCategory(String category) {
        return perksByCategory.getOrDefault(category.toLowerCase(), Collections.emptyList());
    }

    /**
     * Gets all loaded perk definitions.
     *
     * @return Unmodifiable collection of all perks
     */
    public Collection<PerkDefinition> getAllPerks() {
        return Collections.unmodifiableCollection(perksByKey.values());
    }

    /**
     * Gets all unique tool types that have perks defined.
     *
     * @return Set of tool type strings
     */
    public Set<String> getToolTypes() {
        return Collections.unmodifiableSet(perksByTool.keySet());
    }

    /**
     * Gets all unique category keys.
     * Delegates to ConfigManager where categories are now defined.
     *
     * @return Set of category keys
     */
    public Set<String> getCategories() {
        if (configManager != null) {
            return configManager.getCategoryKeys();
        }
        return Collections.emptySet();
    }

    /**
     * Gets category configuration by key.
     * Delegates to ConfigManager where categories are now defined.
     *
     * @param categoryKey The category key
     * @return The CategoryConfig from ConfigManager
     */
    public ConfigManager.CategoryConfig getCategoryConfig(String categoryKey) {
        if (configManager != null) {
            return configManager.getCategory(categoryKey);
        }
        return ConfigManager.CategoryConfig.DEFAULT;
    }

    /**
     * Calculates total weight for perks of a specific tool type.
     *
     * @param toolType The tool type
     * @return The total chance weight sum
     */
    public double getTotalWeightForTool(String toolType) {
        return getPerksForTool(toolType).stream()
            .mapToDouble(PerkDefinition::chance)
            .sum();
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
     * Record representing a complete perk definition from config.
     */
    public record PerkDefinition(
        String key,
        String displayName,
        String description,
        String tool,
        String category,
        double chance,
        String material,
        List<String> commands,
        Map<Integer, PerkLevel> levels
    ) {
        /**
         * Gets a specific level's data.
         *
         * @param level The level number
         * @return The PerkLevel or null
         */
        public PerkLevel getLevel(int level) {
            return levels.get(level);
        }

        /**
         * Gets the maximum level for this perk.
         *
         * @return The max level number
         */
        public int getMaxLevel() {
            return levels.keySet().stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(1);
        }

        /**
         * Checks if the material is a custom texture (base64 head).
         *
         * @return true if custom texture
         */
        public boolean hasCustomTexture() {
            return material != null && material.toLowerCase().startsWith("texture-");
        }

        /**
         * Gets the base64 texture value if this is a custom head.
         *
         * @return The texture value or null
         */
        public String getTextureValue() {
            if (!hasCustomTexture()) return null;
            return material.substring("texture-".length());
        }

        /**
         * Gets the Material for the icon.
         *
         * @return The Material or ENCHANTED_BOOK as fallback
         */
        public Material getMaterial() {
            if (hasCustomTexture()) {
                return Material.PLAYER_HEAD;
            }
            try {
                return Material.valueOf(material.toUpperCase());
            } catch (IllegalArgumentException e) {
                return Material.ENCHANTED_BOOK;
            }
        }
    }

    /**
     * Record representing a single perk level's boost data.
     */
    public record PerkLevel(String[] boostTypes, double[] boostAmounts) {

        /**
         * Gets a map of boost type to amount.
         *
         * @return Map of boost types to their amounts
         */
        public Map<String, Double> getBoostMap() {
            Map<String, Double> map = new HashMap<>();
            int len = Math.min(boostTypes.length, boostAmounts.length);
            for (int i = 0; i < len; i++) {
                map.put(boostTypes[i], boostAmounts[i]);
            }
            return map;
        }

        /**
         * Gets a formatted description of all boosts.
         *
         * @return String like "+10% money, +5% orbs"
         */
        public String getBoostDescription() {
            StringBuilder sb = new StringBuilder();
            int len = Math.min(boostTypes.length, boostAmounts.length);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(", ");
                sb.append("+").append(boostAmounts[i]).append("% ").append(boostTypes[i]);
            }
            return sb.toString();
        }
    }

    // NOTE: CategoryConfig record removed - categories are now defined in config.yml
    // and accessed via ConfigManager.CategoryConfig
}
