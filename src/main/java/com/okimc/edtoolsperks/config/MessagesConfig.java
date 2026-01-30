package com.okimc.edtoolsperks.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles all player-facing messages and localization.
 * Loads messages from messages.yml with placeholder support.
 *
 * <p>Supports both legacy color codes (&amp;a, &amp;b, etc.) and MiniMessage format.</p>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class MessagesConfig {

    private static final String FILE_NAME = "messages.yml";

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    // Cached prefix for quick access
    private String prefix;

    // Reference to ConfigManager for category info (set after construction)
    private ConfigManager configManager;

    /**
     * Creates a new MessagesConfig instance.
     *
     * @param plugin The main plugin instance
     */
    public MessagesConfig(JavaPlugin plugin) {
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
     * Loads the messages configuration file.
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

        // Cache prefix (stored at root level in YAML)
        this.prefix = translateColors(config.getString("prefix", "&8[&eEdToolsPerks&8] "));

        // NOTE: Categories are now loaded from config.yml via ConfigManager
    }

    /**
     * Reloads the messages configuration.
     */
    public void reload() {
        load();
    }

    /**
     * Gets a raw message from the configuration by path.
     *
     * @param path The configuration path (e.g., "general.reload-success")
     * @return The raw message string, or the path if not found
     */
    public String getRaw(String path) {
        return config.getString(path, path);
    }

    /**
     * Gets a translated message with color codes applied.
     *
     * @param path The configuration path
     * @return The translated message with colors
     */
    public String get(String path) {
        return translateColors(getRaw(path));
    }

    /**
     * Gets a message with prefix prepended.
     *
     * @param path The configuration path
     * @return The message with prefix and colors
     */
    public String getWithPrefix(String path) {
        return prefix + get(path);
    }

    /**
     * Gets a message with placeholders replaced.
     *
     * @param path         The configuration path
     * @param replacements Key-value pairs for placeholder replacement (key, value, key, value, ...)
     * @return The message with placeholders replaced and colors applied
     */
    public String get(String path, Object... replacements) {
        String message = getRaw(path);
        message = replacePlaceholders(message, replacements);
        return translateColors(message);
    }

    /**
     * Gets a message with prefix and placeholders replaced.
     *
     * @param path         The configuration path
     * @param replacements Key-value pairs for placeholder replacement
     * @return The message with prefix, placeholders, and colors
     */
    public String getWithPrefix(String path, Object... replacements) {
        return prefix + get(path, replacements);
    }

    /**
     * Gets a list of messages from the configuration.
     *
     * @param path The configuration path
     * @return List of translated messages
     */
    public List<String> getList(String path) {
        return config.getStringList(path).stream()
            .map(this::translateColors)
            .toList();
    }

    /**
     * Gets a list of messages with placeholders replaced.
     *
     * @param path         The configuration path
     * @param replacements Key-value pairs for placeholder replacement
     * @return List of messages with placeholders replaced
     */
    public List<String> getList(String path, Object... replacements) {
        return config.getStringList(path).stream()
            .map(line -> translateColors(replacePlaceholders(line, replacements)))
            .toList();
    }

    /**
     * Sends a message to a player with prefix.
     *
     * @param player The player to send to
     * @param path   The configuration path
     */
    public void send(Player player, String path) {
        player.sendMessage(getWithPrefix(path));
    }

    /**
     * Sends a message to a player with prefix and placeholders.
     *
     * @param player       The player to send to
     * @param path         The configuration path
     * @param replacements Key-value pairs for placeholder replacement
     */
    public void send(Player player, String path, Object... replacements) {
        player.sendMessage(getWithPrefix(path, replacements));
    }

    /**
     * Sends a raw message without prefix to a player.
     *
     * @param player The player to send to
     * @param path   The configuration path
     */
    public void sendRaw(Player player, String path) {
        player.sendMessage(get(path));
    }

    /**
     * Sends a raw message without prefix with placeholders to a player.
     *
     * @param player       The player to send to
     * @param path         The configuration path
     * @param replacements Key-value pairs for placeholder replacement
     */
    public void sendRaw(Player player, String path, Object... replacements) {
        player.sendMessage(get(path, replacements));
    }

    /**
     * Sends a message to a CommandSender with prefix.
     *
     * @param sender The CommandSender to send to
     * @param path   The configuration path
     */
    public void send(org.bukkit.command.CommandSender sender, String path) {
        sender.sendMessage(getWithPrefix(path));
    }

    /**
     * Sends a message to a CommandSender with prefix and placeholders.
     *
     * @param sender       The CommandSender to send to
     * @param path         The configuration path
     * @param replacements Key-value pairs for placeholder replacement
     */
    public void send(org.bukkit.command.CommandSender sender, String path, Object... replacements) {
        sender.sendMessage(getWithPrefix(path, replacements));
    }

    /**
     * Sends a raw message without prefix to a CommandSender.
     *
     * @param sender The CommandSender to send to
     * @param path   The configuration path
     */
    public void sendRaw(org.bukkit.command.CommandSender sender, String path) {
        sender.sendMessage(get(path));
    }

    /**
     * Sends a raw message without prefix with placeholders to a CommandSender.
     *
     * @param sender       The CommandSender to send to
     * @param path         The configuration path
     * @param replacements Key-value pairs for placeholder replacement
     */
    public void sendRaw(org.bukkit.command.CommandSender sender, String path, Object... replacements) {
        sender.sendMessage(get(path, replacements));
    }

    /**
     * Gets the color code for a category.
     * Delegates to ConfigManager where categories are now defined.
     *
     * @param categoryKey The category key (e.g., "comun", "rara")
     * @return The color code (e.g., "&a", "&#9945FF")
     */
    public String getCategoryColor(String categoryKey) {
        if (configManager != null) {
            return configManager.getCategoryColor(categoryKey);
        }
        return "&f"; // Fallback
    }

    /**
     * Gets the display name for a category.
     * Delegates to ConfigManager where categories are now defined.
     *
     * @param categoryKey The category key
     * @return The display name (e.g., "Comun", "Rara")
     */
    public String getCategoryName(String categoryKey) {
        if (configManager != null) {
            return configManager.getCategoryDisplayName(categoryKey);
        }
        return categoryKey != null ? categoryKey : "Unknown"; // Fallback
    }

    /**
     * Gets the prefix string.
     *
     * @return The translated prefix
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Translates legacy color codes (&amp;) and hex colors (&amp;#RRGGBB) to Minecraft color codes.
     *
     * @param text The text to translate
     * @return The translated text
     */
    public String translateColors(String text) {
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
            // Convert to Bukkit's hex format: §x§R§R§G§G§B§B (using unicode escape for §)
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
     * Replaces placeholders in a message.
     *
     * @param message      The message with placeholders
     * @param replacements Key-value pairs (key, value, key, value, ...)
     * @return The message with placeholders replaced
     */
    public String replacePlaceholders(String message, Object... replacements) {
        if (message == null || replacements == null || replacements.length < 2) {
            return message;
        }

        String result = message;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String key = String.valueOf(replacements[i]);
            String value = String.valueOf(replacements[i + 1]);

            // Support both {key} and %key% formats
            result = result.replace("{" + key + "}", value);
            result = result.replace("%" + key + "%", value);
        }

        return result;
    }

    /**
     * Converts a legacy color string to Adventure Component.
     *
     * @param legacyText The text with legacy color codes
     * @return The Adventure Component
     */
    public Component toComponent(String legacyText) {
        String translated = translateColors(legacyText);
        return LegacyComponentSerializer.legacySection().deserialize(translated);
    }

    /**
     * Gets the underlying FileConfiguration.
     *
     * @return The FileConfiguration
     */
    public FileConfiguration getConfig() {
        return config;
    }

    // NOTE: CategoryInfo record removed - categories are now defined in config.yml
    // and accessed via ConfigManager.getCategory()
}
