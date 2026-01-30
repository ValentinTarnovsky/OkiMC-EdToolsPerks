package com.okimc.edtoolsperks.utils;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Fluent builder for creating ItemStacks with customizations.
 *
 * <p>Usage example:</p>
 * <pre>
 * ItemStack item = new ItemBuilder(Material.DIAMOND_SWORD)
 *     .name("&amp;6Epic Sword")
 *     .lore("&amp;7A powerful weapon", "&amp;7Damage: &amp;c+50")
 *     .enchant(Enchantment.DAMAGE_ALL, 5)
 *     .glow()
 *     .build();
 * </pre>
 *
 * @author OkiMC
 * @version 2.0.0
 */
public class ItemBuilder {

    private final ItemStack item;
    private ItemMeta meta;

    /**
     * Creates a new ItemBuilder with the specified material.
     *
     * @param material The material
     */
    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    /**
     * Creates a new ItemBuilder with material and amount.
     *
     * @param material The material
     * @param amount   The stack amount
     */
    public ItemBuilder(Material material, int amount) {
        this.item = new ItemStack(material, amount);
        this.meta = item.getItemMeta();
    }

    /**
     * Creates a new ItemBuilder from an existing ItemStack.
     *
     * @param item The item to clone
     */
    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
        this.meta = this.item.getItemMeta();
    }

    /**
     * Creates an ItemBuilder from a material name string.
     *
     * @param materialName The material name
     * @return The ItemBuilder, or one with STONE if material not found
     */
    public static ItemBuilder of(String materialName) {
        try {
            Material material = Material.valueOf(materialName.toUpperCase());
            return new ItemBuilder(material);
        } catch (IllegalArgumentException e) {
            return new ItemBuilder(Material.STONE);
        }
    }

    // ==================== Name & Lore ====================

    /**
     * Sets the display name of the item.
     *
     * @param name The display name (color codes supported)
     * @return This builder
     */
    public ItemBuilder name(String name) {
        if (meta != null && name != null) {
            meta.setDisplayName(MessageUtils.colorize(name));
        }
        return this;
    }

    /**
     * Sets the lore of the item.
     *
     * @param lore The lore lines (color codes supported)
     * @return This builder
     */
    public ItemBuilder lore(String... lore) {
        if (meta != null && lore != null) {
            meta.setLore(MessageUtils.colorize(Arrays.asList(lore)));
        }
        return this;
    }

    /**
     * Sets the lore of the item from a list.
     *
     * @param lore The lore lines
     * @return This builder
     */
    public ItemBuilder lore(List<String> lore) {
        if (meta != null && lore != null) {
            meta.setLore(MessageUtils.colorize(lore));
        }
        return this;
    }

    /**
     * Adds lines to the existing lore.
     *
     * @param lines The lines to add
     * @return This builder
     */
    public ItemBuilder addLore(String... lines) {
        if (meta != null && lines != null) {
            List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            for (String line : lines) {
                lore.add(MessageUtils.colorize(line));
            }
            meta.setLore(lore);
        }
        return this;
    }

    /**
     * Adds lines to the existing lore from a list.
     *
     * @param lines The lines to add
     * @return This builder
     */
    public ItemBuilder addLore(List<String> lines) {
        if (meta != null && lines != null) {
            List<String> lore = meta.getLore();
            if (lore == null) {
                lore = new ArrayList<>();
            }
            for (String line : lines) {
                lore.add(MessageUtils.colorize(line));
            }
            meta.setLore(lore);
        }
        return this;
    }

    /**
     * Replaces placeholders in name and lore.
     *
     * @param replacements Key-value pairs
     * @return This builder
     */
    public ItemBuilder replace(Object... replacements) {
        if (meta != null) {
            if (meta.hasDisplayName()) {
                meta.setDisplayName(MessageUtils.replace(meta.getDisplayName(), replacements));
            }
            if (meta.hasLore()) {
                meta.setLore(MessageUtils.replace(meta.getLore(), replacements));
            }
        }
        return this;
    }

    /**
     * Replaces placeholders using a map.
     *
     * @param replacements Map of placeholder keys to values
     * @return This builder
     */
    public ItemBuilder replace(Map<String, String> replacements) {
        if (meta != null && replacements != null) {
            if (meta.hasDisplayName()) {
                meta.setDisplayName(MessageUtils.replace(meta.getDisplayName(), replacements));
            }
            if (meta.hasLore()) {
                List<String> newLore = new ArrayList<>();
                for (String line : meta.getLore()) {
                    newLore.add(MessageUtils.replace(line, replacements));
                }
                meta.setLore(newLore);
            }
        }
        return this;
    }

    // ==================== Amount & Durability ====================

    /**
     * Sets the stack amount.
     *
     * @param amount The amount
     * @return This builder
     */
    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    /**
     * Sets the custom model data.
     *
     * @param data The custom model data
     * @return This builder
     */
    public ItemBuilder customModelData(int data) {
        if (meta != null) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    // ==================== Enchantments ====================

    /**
     * Adds an enchantment to the item.
     *
     * @param enchantment The enchantment
     * @param level       The level
     * @return This builder
     */
    public ItemBuilder enchant(Enchantment enchantment, int level) {
        if (meta != null && enchantment != null) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    /**
     * Removes an enchantment from the item.
     *
     * @param enchantment The enchantment
     * @return This builder
     */
    public ItemBuilder removeEnchant(Enchantment enchantment) {
        if (meta != null && enchantment != null) {
            meta.removeEnchant(enchantment);
        }
        return this;
    }

    /**
     * Adds a glowing effect without visible enchantments.
     *
     * @return This builder
     */
    public ItemBuilder glow() {
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    // ==================== Item Flags ====================

    /**
     * Adds item flags to hide attributes.
     *
     * @param flags The flags to add
     * @return This builder
     */
    public ItemBuilder flags(ItemFlag... flags) {
        if (meta != null && flags != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    /**
     * Hides all item attributes.
     *
     * @return This builder
     */
    public ItemBuilder hideAll() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.values());
        }
        return this;
    }

    /**
     * Hides enchantments from display.
     *
     * @return This builder
     */
    public ItemBuilder hideEnchants() {
        return flags(ItemFlag.HIDE_ENCHANTS);
    }

    /**
     * Hides attributes from display.
     *
     * @return This builder
     */
    public ItemBuilder hideAttributes() {
        return flags(ItemFlag.HIDE_ATTRIBUTES);
    }

    /**
     * Makes the item unbreakable.
     *
     * @return This builder
     */
    public ItemBuilder unbreakable() {
        if (meta != null) {
            meta.setUnbreakable(true);
        }
        return this;
    }

    // ==================== Persistent Data ====================

    /**
     * Stores a string in the item's persistent data.
     *
     * @param plugin The plugin
     * @param key    The key name
     * @param value  The value
     * @return This builder
     */
    public ItemBuilder data(JavaPlugin plugin, String key, String value) {
        if (meta != null && plugin != null && key != null) {
            NamespacedKey nsKey = new NamespacedKey(plugin, key);
            meta.getPersistentDataContainer().set(nsKey, PersistentDataType.STRING, value);
        }
        return this;
    }

    /**
     * Stores an integer in the item's persistent data.
     *
     * @param plugin The plugin
     * @param key    The key name
     * @param value  The value
     * @return This builder
     */
    public ItemBuilder data(JavaPlugin plugin, String key, int value) {
        if (meta != null && plugin != null && key != null) {
            NamespacedKey nsKey = new NamespacedKey(plugin, key);
            meta.getPersistentDataContainer().set(nsKey, PersistentDataType.INTEGER, value);
        }
        return this;
    }

    // ==================== Special Items ====================

    /**
     * Sets the skull owner for player heads.
     *
     * @param playerName The player name
     * @return This builder
     */
    public ItemBuilder skullOwner(String playerName) {
        if (meta instanceof SkullMeta skullMeta && playerName != null) {
            skullMeta.setOwner(playerName);
        }
        return this;
    }

    /**
     * Sets a custom skull texture from base64.
     *
     * @param base64Texture The base64 texture value
     * @return This builder
     */
    public ItemBuilder skullTexture(String base64Texture) {
        if (item.getType() == Material.PLAYER_HEAD && base64Texture != null) {
            ItemStack texturedHead = SkullUtils.createCustomHead(base64Texture);
            this.meta = texturedHead.getItemMeta();
        }
        return this;
    }

    /**
     * Sets the color for leather armor.
     *
     * @param color The color
     * @return This builder
     */
    public ItemBuilder leatherColor(Color color) {
        if (meta instanceof LeatherArmorMeta leatherMeta && color != null) {
            leatherMeta.setColor(color);
        }
        return this;
    }

    /**
     * Adds a potion effect.
     *
     * @param effectType The effect type
     * @param duration   The duration in ticks
     * @param amplifier  The amplifier (level - 1)
     * @return This builder
     */
    public ItemBuilder potionEffect(PotionEffectType effectType, int duration, int amplifier) {
        if (meta instanceof PotionMeta potionMeta && effectType != null) {
            potionMeta.addCustomEffect(new PotionEffect(effectType, duration, amplifier), true);
        }
        return this;
    }

    /**
     * Sets the potion color.
     *
     * @param color The color
     * @return This builder
     */
    public ItemBuilder potionColor(Color color) {
        if (meta instanceof PotionMeta potionMeta && color != null) {
            potionMeta.setColor(color);
        }
        return this;
    }

    // ==================== Meta Manipulation ====================

    /**
     * Allows direct modification of the ItemMeta.
     *
     * @param consumer The consumer to modify meta
     * @return This builder
     */
    public ItemBuilder meta(Consumer<ItemMeta> consumer) {
        if (meta != null && consumer != null) {
            consumer.accept(meta);
        }
        return this;
    }

    /**
     * Allows typed modification of specific meta types.
     *
     * @param metaClass The meta class type
     * @param consumer  The consumer to modify meta
     * @param <T>       The meta type
     * @return This builder
     */
    @SuppressWarnings("unchecked")
    public <T extends ItemMeta> ItemBuilder meta(Class<T> metaClass, Consumer<T> consumer) {
        if (meta != null && metaClass.isInstance(meta) && consumer != null) {
            consumer.accept((T) meta);
        }
        return this;
    }

    // ==================== Build ====================

    /**
     * Builds and returns the final ItemStack.
     *
     * @return The constructed ItemStack
     */
    public ItemStack build() {
        if (meta != null) {
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Builds and returns a clone of the final ItemStack.
     *
     * @return A clone of the constructed ItemStack
     */
    public ItemStack buildClone() {
        return build().clone();
    }

    /**
     * Gets the current ItemMeta.
     *
     * @return The ItemMeta
     */
    public ItemMeta getMeta() {
        return meta;
    }

    /**
     * Gets the underlying ItemStack (not finalized).
     *
     * @return The ItemStack
     */
    public ItemStack getItem() {
        return item;
    }
}
