package com.okimc.edtoolsperks.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for message formatting and color translation.
 *
 * <p>Supports:</p>
 * <ul>
 *   <li>Legacy color codes (&amp;a, &amp;b, etc.)</li>
 *   <li>Hex color codes (&amp;#RRGGBB)</li>
 *   <li>Placeholder replacement ({key} and %key%)</li>
 *   <li>Adventure API integration</li>
 * </ul>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public final class MessageUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("[{%]([^{}%]+)[}%]");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");
    private static final DecimalFormat PERCENTAGE_FORMAT = new DecimalFormat("#,##0.#%");

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER =
        LegacyComponentSerializer.legacySection();

    private MessageUtils() {
        // Utility class - no instantiation
    }

    // ==================== Color Translation ====================

    /**
     * Translates color codes in a string.
     * Supports both legacy (&amp;) and hex (&amp;#RRGGBB) formats.
     *
     * @param text The text to translate
     * @return The translated text with color codes applied
     */
    public static String colorize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Process hex colors first
        text = translateHexColors(text);

        // Then process legacy colors
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Translates hex color codes (&amp;#RRGGBB) to Bukkit format.
     *
     * @param text The text containing hex codes
     * @return The text with hex codes translated
     */
    public static String translateHexColors(String text) {
        if (text == null) return "";

        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder builder = new StringBuilder(text.length() + 32);

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char c : hex.toCharArray()) {
                replacement.append('\u00A7').append(c);
            }
            matcher.appendReplacement(builder, replacement.toString());
        }
        matcher.appendTail(builder);

        return builder.toString();
    }

    /**
     * Strips all color codes from a string.
     *
     * @param text The text to strip
     * @return The text without color codes
     */
    public static String stripColors(String text) {
        if (text == null) return "";
        return ChatColor.stripColor(colorize(text));
    }

    /**
     * Colorizes a list of strings.
     *
     * @param lines The list of strings
     * @return A new list with colorized strings
     */
    public static List<String> colorize(List<String> lines) {
        if (lines == null) return new ArrayList<>();

        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(colorize(line));
        }
        return result;
    }

    // ==================== Placeholder Replacement ====================

    /**
     * Replaces placeholders in a string.
     * Supports both {key} and %key% formats.
     *
     * @param text         The text with placeholders
     * @param replacements Key-value pairs (key, value, key, value, ...)
     * @return The text with placeholders replaced
     */
    public static String replace(String text, Object... replacements) {
        if (text == null || replacements == null || replacements.length < 2) {
            return text != null ? text : "";
        }

        String result = text;
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String key = String.valueOf(replacements[i]);
            String value = String.valueOf(replacements[i + 1]);

            result = result.replace("{" + key + "}", value);
            result = result.replace("%" + key + "%", value);
        }

        return result;
    }

    /**
     * Replaces placeholders using a map.
     *
     * @param text         The text with placeholders
     * @param replacements Map of placeholder keys to values
     * @return The text with placeholders replaced
     */
    public static String replace(String text, Map<String, String> replacements) {
        if (text == null || replacements == null || replacements.isEmpty()) {
            return text != null ? text : "";
        }

        String result = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
            result = result.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        return result;
    }

    /**
     * Replaces placeholders in a list of strings.
     *
     * @param lines        The list of strings
     * @param replacements Key-value pairs
     * @return A new list with placeholders replaced
     */
    public static List<String> replace(List<String> lines, Object... replacements) {
        if (lines == null) return new ArrayList<>();

        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(replace(line, replacements));
        }
        return result;
    }

    /**
     * Colorizes and replaces placeholders in one call.
     *
     * @param text         The text to process
     * @param replacements Key-value pairs
     * @return The processed text
     */
    public static String format(String text, Object... replacements) {
        return colorize(replace(text, replacements));
    }

    /**
     * Colorizes and replaces placeholders in a list.
     *
     * @param lines        The list of strings
     * @param replacements Key-value pairs
     * @return A new processed list
     */
    public static List<String> format(List<String> lines, Object... replacements) {
        if (lines == null) return new ArrayList<>();

        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(format(line, replacements));
        }
        return result;
    }

    // ==================== Number Formatting ====================

    /**
     * Formats a number with thousand separators.
     *
     * @param number The number to format
     * @return The formatted string
     */
    public static String formatNumber(double number) {
        return DECIMAL_FORMAT.format(number);
    }

    /**
     * Formats a number as a percentage.
     *
     * @param decimal The decimal value (0.10 = 10%)
     * @return The formatted percentage string
     */
    public static String formatPercentage(double decimal) {
        return PERCENTAGE_FORMAT.format(decimal);
    }

    /**
     * Formats a number with a plus sign if positive.
     *
     * @param number The number
     * @return The formatted string with sign
     */
    public static String formatWithSign(double number) {
        if (number > 0) {
            return "+" + formatNumber(number);
        }
        return formatNumber(number);
    }

    // ==================== Message Sending ====================

    /**
     * Sends a colorized message to a command sender.
     *
     * @param sender  The recipient
     * @param message The message
     */
    public static void send(CommandSender sender, String message) {
        if (sender == null || message == null) return;
        sender.sendMessage(colorize(message));
    }

    /**
     * Sends a colorized message with placeholders to a command sender.
     *
     * @param sender       The recipient
     * @param message      The message
     * @param replacements Key-value pairs
     */
    public static void send(CommandSender sender, String message, Object... replacements) {
        if (sender == null || message == null) return;
        sender.sendMessage(format(message, replacements));
    }

    /**
     * Sends multiple colorized messages to a command sender.
     *
     * @param sender   The recipient
     * @param messages The messages
     */
    public static void send(CommandSender sender, List<String> messages) {
        if (sender == null || messages == null) return;
        for (String message : messages) {
            sender.sendMessage(colorize(message));
        }
    }

    /**
     * Broadcasts a colorized message to all online players.
     *
     * @param message The message
     */
    public static void broadcast(String message) {
        String formatted = colorize(message);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(formatted);
        }
    }

    /**
     * Sends a colorized message to the console.
     *
     * @param message The message
     */
    public static void sendConsole(String message) {
        Bukkit.getConsoleSender().sendMessage(colorize(message));
    }

    // ==================== Adventure API ====================

    /**
     * Converts a legacy color string to Adventure Component.
     *
     * @param legacyText The text with legacy color codes
     * @return The Adventure Component
     */
    public static Component toComponent(String legacyText) {
        if (legacyText == null) return Component.empty();
        return LEGACY_SERIALIZER.deserialize(colorize(legacyText));
    }

    /**
     * Parses MiniMessage format to Adventure Component.
     *
     * @param miniMessageText The MiniMessage formatted text
     * @return The Adventure Component
     */
    public static Component parseMiniMessage(String miniMessageText) {
        if (miniMessageText == null) return Component.empty();
        return MINI_MESSAGE.deserialize(miniMessageText);
    }

    /**
     * Converts an Adventure Component to legacy string.
     *
     * @param component The Adventure Component
     * @return The legacy formatted string
     */
    public static String fromComponent(Component component) {
        if (component == null) return "";
        return LEGACY_SERIALIZER.serialize(component);
    }

    // ==================== Validation ====================

    /**
     * Checks if a string is null or empty.
     *
     * @param text The string to check
     * @return true if null or empty
     */
    public static boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }

    /**
     * Checks if a string is null, empty, or only whitespace.
     *
     * @param text The string to check
     * @return true if null, empty, or blank
     */
    public static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /**
     * Returns the first non-null, non-empty string.
     *
     * @param values The strings to check
     * @return The first valid string, or empty string
     */
    public static String coalesce(String... values) {
        for (String value : values) {
            if (!isEmpty(value)) {
                return value;
            }
        }
        return "";
    }
}
