
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

    public static ItemStack createRadioItem(CivLabsRadiosPlugin plugin) {
        String blockType = plugin.getConfig().getString("baseBlock", "BARREL");
        ItemStack it = new ItemStack(Material.valueOf(blockType));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Radio"));

        int cmd = plugin.getConfig().getInt("crafting.customModelData", 0);
        if (cmd > 0) {
            m.setCustomModelData(cmd);
        }

        it.setItemMeta(m);
        return it;
    }

    public static boolean isRadioItem(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta() || stack.getItemMeta().displayName() == null) {
            return false;
        }

        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
            .serialize(stack.getItemMeta().displayName());

        return "Radio".equalsIgnoreCase(name);
    }

    public static void registerRecipeIfEnabled(CivLabsRadiosPlugin plugin) {
        if (!plugin.getConfig().getBoolean("crafting.enabled", false)) return;

        ShapedRecipe recipe = new ShapedRecipe(
            new NamespacedKey(plugin, "radio"),
            createRadioItem(plugin)
        );

        var shape = plugin.getConfig().getStringList("crafting.recipe.shape");
        recipe.shape(shape.toArray(new String[0]));

        var ing = plugin.getConfig().getConfigurationSection("crafting.recipe.ingredients");
        for (String c : ing.getKeys(false)) {
            recipe.setIngredient(c.charAt(0), Material.valueOf(ing.getString(c)));
        }

        Bukkit.addRecipe(recipe);
    }
}
