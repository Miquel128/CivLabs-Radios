package com.civlabs.radios.util;

import com.civlabs.radios.CivLabsRadiosPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

public class ItemUtil {

    /**
     * Always create the Radio item as a SMOKER (not a barrel).
     */
    public static ItemStack createRadioItem(CivLabsRadiosPlugin plugin) {
        ItemStack it = new ItemStack(Material.SMOKER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Radio"));

        int cmd = plugin.getConfig().getInt("crafting.customModelData", 0);
        if (cmd > 0) {
            m.setCustomModelData(cmd);
        }

        it.setItemMeta(m);
        return it;
    }

    /**
     * Identify our Radio item by display name.
     */
    public static boolean isRadioItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || stack.getItemMeta().displayName() == null) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(stack.getItemMeta().displayName());
        return "Radio".equalsIgnoreCase(name);
    }

    /**
     * Register (or re-register) the crafting recipe.
     * - Removes any old "radio" recipe (which might output a BARREL)
     * - Registers a new "radio_smoker" recipe that outputs a SMOKER
     */
    public static void registerRecipeIfEnabled(CivLabsRadiosPlugin plugin) {
        if (!plugin.getConfig().getBoolean("crafting.enabled", false)) return;

        // Remove legacy recipe if it exists (might still output a barrel)
        try {
            Bukkit.removeRecipe(new NamespacedKey(plugin, "radio"));
        } catch (Throwable ignored) {}

        // Register a new recipe key to avoid collisions with cached data
        NamespacedKey key = new NamespacedKey(plugin, "radio_smoker");
        ShapedRecipe recipe = new ShapedRecipe(key, createRadioItem(plugin));

        var shape = plugin.getConfig().getStringList("crafting.recipe.shape");
        if (shape == null || shape.isEmpty()) {
            // default simple shape if none configured
            recipe.shape("ICI", "RSR", "IPI");
            // You can still override ingredients via config if you want
            recipe.setIngredient('I', Material.IRON_INGOT);
            recipe.setIngredient('C', Material.COPPER_INGOT);
            recipe.setIngredient('R', Material.REDSTONE);
            recipe.setIngredient('S', Material.SMOKER);
            recipe.setIngredient('P', Material.PAPER);
        } else {
            recipe.shape(shape.toArray(new String[0]));
            var ing = plugin.getConfig().getConfigurationSection("crafting.recipe.ingredients");
            if (ing != null) {
                for (String c : ing.getKeys(false)) {
                    String mat = ing.getString(c);
                    if (mat != null) {
                        recipe.setIngredient(c.charAt(0), Material.valueOf(mat));
                    }
                }
            }
        }

        Bukkit.addRecipe(recipe);
    }
}
