
package com.civlabs.radios.gui;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.Radio;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class FrequencyOverviewGui implements Listener {

    private static final int ITEMS_PER_PAGE = 45;
    private static final Map<UUID, Integer> playerPages = new HashMap<>();

    public static void open(CivLabsRadiosPlugin plugin, Player viewer, Radio returnToRadio, int page) {
        List<Radio> allRadios = plugin.store().getAll();
        Map<Integer, Radio> activeFrequencies = allRadios.stream()
            .filter(Radio::isEnabled)
            .collect(Collectors.toMap(
                Radio::getTransmitFrequency,
                r -> r,
                (r1, r2) -> r1,
                TreeMap::new
            ));

        List<Map.Entry<Integer, Radio>> entries = new ArrayList<>(activeFrequencies.entrySet());
        int totalPages = Math.max(1, (int) Math.ceil((double) entries.size() / ITEMS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));
        
        playerPages.put(viewer.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(
            new FrequencyOverviewHolder(returnToRadio.getId()),
            54,
            Component.text("§6Active Frequencies §8(Page " + (page + 1) + "/" + totalPages + ")")
        );

        int startIdx = page * ITEMS_PER_PAGE;
        int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, entries.size());

        boolean showCoords = plugin.showCoordinates();

        int slot = 0;
        for (int i = startIdx; i < endIdx; i++) {
            Map.Entry<Integer, Radio> entry = entries.get(i);
            int freq = entry.getKey();
            Radio activeRadio = entry.getValue();
            
            String operatorName = "Unknown";
            if (activeRadio.getOperator() != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(activeRadio.getOperator());
                operatorName = op.getName() != null ? op.getName() : "Unknown";
            }
            
            ItemStack item = new ItemStack(Material.REDSTONE_TORCH);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("§c⚡ Frequency " + freq));
            
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("§7Operator: §e" + operatorName));
            lore.add(Component.text("§7World: §e" + activeRadio.getWorld()));
            lore.add(Component.text("§7Dimension: §e" + activeRadio.getDimension()));
            
            if (showCoords) {
                lore.add(Component.text("§7Location: §e" + 
                    activeRadio.getX() + ", " + 
                    activeRadio.getY() + ", " + 
                    activeRadio.getZ()));
            }
            
            lore.add(Component.text("§c§l● OCCUPIED"));
            
            meta.lore(lore);
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }

        int maxFreq = plugin.getMaxFrequencies();
        int availableCount = maxFreq - activeFrequencies.size();
        
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.displayName(Component.text("§6ℹ Statistics"));
        infoMeta.lore(List.of(
            Component.text("§7Total Frequencies: §e" + maxFreq),
            Component.text("§7Active: §c" + activeFrequencies.size()),
            Component.text("§7Available: §a" + availableCount),
            Component.text(""),
            Component.text("§7Showing: §e" + (startIdx + 1) + "-" + endIdx + " §7of §e" + entries.size()),
            Component.text(""),
            Component.text("§8Coords: " + (showCoords ? "§aShown" : "§cHidden"))
        ));
        info.setItemMeta(infoMeta);
        inv.setItem(49, info);

        if (page > 0) {
            ItemStack prevPage = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevPage.getItemMeta();
            prevMeta.displayName(Component.text("§e◀ Previous Page"));
            prevMeta.lore(List.of(Component.text("§7Go to page " + page)));
            prevPage.setItemMeta(prevMeta);
            inv.setItem(48, prevPage);
        }

        if (page < totalPages - 1) {
            ItemStack nextPage = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextPage.getItemMeta();
            nextMeta.displayName(Component.text("§eNext Page ▶"));
            nextMeta.lore(List.of(Component.text("§7Go to page " + (page + 2))));
            nextPage.setItemMeta(nextMeta);
            inv.setItem(50, nextPage);
        }

        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        backMeta.displayName(Component.text("§c✕ Close"));
        backMeta.lore(List.of(Component.text("§7Return to radio controls")));
        backButton.setItemMeta(backMeta);
        inv.setItem(45, backButton);

        if (entries.isEmpty()) {
            ItemStack emptyMsg = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta emptyMeta = emptyMsg.getItemMeta();
            emptyMeta.displayName(Component.text("§a✓ All Frequencies Available!"));
            emptyMeta.lore(List.of(
                Component.text("§7No active transmitters found."),
                Component.text("§7All " + maxFreq + " frequencies are free!")
            ));
            emptyMsg.setItemMeta(emptyMeta);
            inv.setItem(22, emptyMsg);
        }

        viewer.openInventory(inv);
    }

    public static void open(CivLabsRadiosPlugin plugin, Player viewer, Radio returnToRadio) {
        open(plugin, viewer, returnToRadio, playerPages.getOrDefault(viewer.getUniqueId(), 0));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof FrequencyOverviewHolder holder)) return;

        e.setCancelled(true);

        CivLabsRadiosPlugin plugin = (CivLabsRadiosPlugin) Bukkit.getPluginManager().getPlugin("CivLabsRadios");
        if (plugin == null) return;

        Radio r = plugin.store().get(holder.getRadioId());
        if (r == null) {
            p.closeInventory();
            return;
        }

        int currentPage = playerPages.getOrDefault(p.getUniqueId(), 0);

        if (e.getSlot() == 45) {
            p.closeInventory();
            playerPages.remove(p.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.getRadioMode() == com.civlabs.radios.core.RadioMode.SLIDER) {
                    SliderGui.open(plugin, p, r);
                } else {
                    RadioGui.open(plugin, p, r);
                }
            });
        } else if (e.getSlot() == 48) {
            open(plugin, p, r, currentPage - 1);
            plugin.sounds().playClick(p);
        } else if (e.getSlot() == 50) {
            open(plugin, p, r, currentPage + 1);
            plugin.sounds().playClick(p);
        }
    }
}
