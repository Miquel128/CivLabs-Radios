
package com.civlabs.radios.gui;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.Radio;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class SliderGui {

    public static void open(CivLabsRadiosPlugin plugin, Player viewer, Radio r) {
        Inventory inv = org.bukkit.Bukkit.createInventory(
            new RadioGuiHolder(r.getId()),
            54,
            Component.text("Radio Tuner").color(getDimensionColor(r.getDimension()))
        );

        // Top section: TX frequency slider
        renderFrequencySlider(inv, 0, r.getTransmitFrequency(), plugin.getMaxFrequencies(), "TX", true);

        // Middle section: RX frequency slider  
        renderFrequencySlider(inv, 18, r.getListenFrequency(), plugin.getMaxFrequencies(), "RX", false);

        // Bottom section: Controls
        inv.setItem(36, statusItem(r, plugin.getMaxFrequencies()));
        inv.setItem(37, frequencyOverviewItem());
        inv.setItem(40, toggleItem(r.isEnabled()));
        inv.setItem(44, jumpToItem("TX"));
        inv.setItem(45, jumpToItem("RX"));
        inv.setItem(53, closeItem());

        viewer.openInventory(inv);
    }

    private static void renderFrequencySlider(Inventory inv, int startSlot, int currentFreq, int maxFreq, String type, boolean isTx) {
        // Decrease buttons
        inv.setItem(startSlot, decreaseButton(type, 10));
        inv.setItem(startSlot + 1, decreaseButton(type, 1));

        // Current frequency display
        inv.setItem(startSlot + 4, frequencyDisplay(type, currentFreq, maxFreq));

        // Increase buttons
        inv.setItem(startSlot + 7, increaseButton(type, 1));
        inv.setItem(startSlot + 8, increaseButton(type, 10));

        // Visual slider bar
        renderSliderBar(inv, startSlot + 9, currentFreq, maxFreq, isTx);
    }

    private static void renderSliderBar(Inventory inv, int startSlot, int currentFreq, int maxFreq, boolean isTx) {
        int barLength = 9;
        int position = currentFreq > 0 ? (int) (((double) currentFreq / maxFreq) * (barLength - 1)) : 0;

        for (int i = 0; i < barLength; i++) {
            Material mat;
            String name;
            
            if (i == position && currentFreq > 0) {
                mat = isTx ? Material.REDSTONE_BLOCK : Material.LAPIS_BLOCK;
                name = "§e▶ " + currentFreq;
            } else if (i < position) {
                mat = isTx ? Material.RED_STAINED_GLASS_PANE : Material.BLUE_STAINED_GLASS_PANE;
                name = "§7━";
            } else {
                mat = Material.GRAY_STAINED_GLASS_PANE;
                name = "§8━";
            }
            
            inv.setItem(startSlot + i, named(mat, name, null));
        }
    }

    private static TextColor getDimensionColor(String dimension) {
        return switch (dimension) {
            case "NETHER" -> TextColor.color(200, 50, 50);
            case "THE_END" -> TextColor.color(180, 100, 200);
            default -> TextColor.color(255, 255, 255);
        };
    }

    private static ItemStack named(Material m, String name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name));
        if (lore != null && !lore.isEmpty()) meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack decreaseButton(String type, int amount) {
        return named(
            Material.RED_CONCRETE,
            "§c◀ -" + amount,
            List.of(
                Component.text("§7Decrease " + type + " frequency"),
                Component.text("§7by §c" + amount)
            )
        );
    }

    private static ItemStack increaseButton(String type, int amount) {
        return named(
            Material.LIME_CONCRETE,
            "§a+" + amount + " §a▶",
            List.of(
                Component.text("§7Increase " + type + " frequency"),
                Component.text("§7by §a" + amount)
            )
        );
    }

    private static ItemStack frequencyDisplay(String type, int freq, int maxFreq) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7Current: §e" + freq));
        lore.add(Component.text("§7Range: §e1-" + maxFreq));
        lore.add(Component.text(""));
        lore.add(Component.text("§8Use +/- buttons to adjust"));
        
        return named(
            type.equals("TX") ? Material.REDSTONE_TORCH : Material.NOTE_BLOCK,
            "§6" + type + " Frequency: §e" + freq,
            lore
        );
    }

    private static ItemStack jumpToItem(String type) {
        return named(
            Material.COMPASS,
            "§b⚡ Jump to " + type,
            List.of(
                Component.text("§7Enter a specific frequency"),
                Component.text("§7via chat input")
            )
        );
    }

    private static ItemStack toggleItem(boolean on) {
        return named(
            on ? Material.REDSTONE_TORCH : Material.LEVER,
            on ? "§c⏻ Disable Radio" : "§a⏻ Enable Radio",
            List.of(
                Component.text(on ? "§7Click to turn OFF" : "§7Click to turn ON"),
                Component.text(on ? "§cCurrently broadcasting" : "§8Currently inactive")
            )
        );
    }

    private static ItemStack closeItem() {
        return named(
            Material.BARRIER,
            "§cClose",
            List.of(Component.text("§7Close this menu"))
        );
    }

    private static ItemStack statusItem(Radio r, int maxFreq) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7TX: §e" + r.getTransmitFrequency()));
        lore.add(Component.text("§7RX: §e" + r.getListenFrequency()));
        lore.add(Component.text("§7Status: " + (r.isEnabled() ? "§a● Active" : "§c● Inactive")));
        lore.add(Component.text("§7Dimension: §e" + r.getDimension()));
        return named(Material.BOOK, "§6📊 Radio Status", lore);
    }

    private static ItemStack frequencyOverviewItem() {
        return named(
            Material.SPYGLASS,
            "§b🔍 Active Frequencies",
            List.of(
                Component.text("§7View all occupied"),
                Component.text("§7frequencies on the server")
            )
        );
    }

    public static void handleClick(CivLabsRadiosPlugin plugin, Player p, Radio r, InventoryClickEvent e) {
        if (e.getCurrentItem() == null || !e.getCurrentItem().hasItemMeta()) return;

        int slot = e.getSlot();
        int maxFreq = plugin.getMaxFrequencies();
        
        // TX controls (row 1)
        if (slot == 0) { // TX -10
            r.setTransmitFrequency(Math.max(1, r.getTransmitFrequency() - 10));
            plugin.store().save(r);
            plugin.sounds().playFrequencyChange(p);
        } else if (slot == 1) { // TX -1
            r.setTransmitFrequency(Math.max(1, r.getTransmitFrequency() - 1));
            plugin.store().save(r);
            plugin.sounds().playFrequencyChange(p);
        } else if (slot == 7) { // TX +1
            r.setTransmitFrequency(Math.min(maxFreq, r.getTransmitFrequency() + 1));
            plugin.store().save(r);
            plugin.sounds().playFrequencyChange(p);
        } else if (slot == 8) { // TX +10
            r.setTransmitFrequency(Math.min(maxFreq, r.getTransmitFrequency() + 10));
            plugin.store().save(r);
            plugin.sounds().playFrequencyChange(p);
        }
        
        // RX controls (row 2)
        else if (slot == 18) { // RX -10
            r.setListenFrequency(Math.max(1, r.getListenFrequency() - 10));
            plugin.store().save(r);
            plugin.voice().updateSpeakerFor(r);
            plugin.sounds().playFrequencyChange(p);
        } else if (slot == 19) { // RX -1
            r.setListenFrequency(Math.max(1, r.getListenFrequency() - 1));
            plugin.store().save(r);
            plugin.voice().updateSpeakerFor(r);
            plugin.sounds().playFrequencyChange(p);
        } else if (slot == 25) { // RX +1
            r.setListenFrequency(Math.min(maxFreq, r.getListenFrequency() + 1));
            plugin.store().save(r);
            plugin.voice().updateSpeakerFor(r);
            plugin.sounds().playFrequencyChange(p);
        } else if (slot == 26) { // RX +10
            r.setListenFrequency(Math.min(maxFreq, r.getListenFrequency() + 10));
            plugin.store().save(r);
            plugin.voice().updateSpeakerFor(r);
            plugin.sounds().playFrequencyChange(p);
        }
        
        // Control buttons (row 3)
        else if (slot == 37) { // Frequency Overview
            p.closeInventory();
            FrequencyOverviewGui.open(plugin, p, r);
            plugin.sounds().playClick(p);
            return;
        } else if (slot == 40) { // Toggle Enable/Disable
            if (r.isEnabled()) {
                plugin.disableRadioIfEnabled(r, com.civlabs.radios.model.DisableReason.ADMIN);
            } else {
                if (r.getTransmitFrequency() >= 1) {
                    plugin.enableRadio(r, p, r.getTransmitFrequency());
                } else {
                    p.sendMessage(Component.text("§c⚠ Set a TX frequency first (use + buttons)"));
                    plugin.sounds().playError(p);
                }
            }
        } else if (slot == 44) { // Jump to TX
            p.closeInventory();
            AnvilInputGui.open(plugin, p, r, true);
            return;
        } else if (slot == 45) { // Jump to RX
            p.closeInventory();
            AnvilInputGui.open(plugin, p, r, false);
            return;
        } else if (slot == 53) { // Close
            p.closeInventory();
            return;
        }

        open(plugin, p, r);
    }
}
