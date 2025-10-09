
package com.civlabs.radios.listener;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.core.RadioMode;
import com.civlabs.radios.gui.AnvilInputGui;
import com.civlabs.radios.gui.RadioGui;
import com.civlabs.radios.gui.RadioGuiHolder;
import com.civlabs.radios.gui.SliderGui;
import com.civlabs.radios.gui.FrequencyOverviewGui;
import com.civlabs.radios.model.Radio;
import com.civlabs.radios.util.Keys;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class RadioInteractListener implements Listener {
    private final CivLabsRadiosPlugin plugin;

    public RadioInteractListener(CivLabsRadiosPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(new AnvilInputGui(), plugin);
        Bukkit.getPluginManager().registerEvents(new FrequencyOverviewGui(), plugin);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;

        Block block = e.getClickedBlock();
        if (!(block.getState() instanceof TileState ts)) return;

        String tag = ts.getPersistentDataContainer().get(Keys.RADIO_ID, PersistentDataType.STRING);
        if (tag == null) return;

        e.setCancelled(true);
        UUID id = UUID.fromString(tag);
        Radio r = plugin.store().get(id);
        if (r == null) return;

        Player p = e.getPlayer();
        
        // Shift-right-click shows operator info
        if (p.isSneaking()) {
            if (r.isEnabled() && r.getOperator() != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(r.getOperator());
                String opName = op.getName() != null ? op.getName() : "Unknown";
                
                p.sendMessage(Component.text("§8§m                                    "));
                p.sendMessage(Component.text("§6📻 Radio Information"));
                p.sendMessage(Component.text("§7Operator: §e" + opName));
                p.sendMessage(Component.text("§7Status: §aActive"));
                p.sendMessage(Component.text("§7TX Frequency: §e" + r.getTransmitFrequency()));
                p.sendMessage(Component.text("§7RX Frequency: §e" + r.getListenFrequency()));
                p.sendMessage(Component.text("§7Dimension: §e" + r.getDimension()));
                p.sendMessage(Component.text("§8§m                                    "));
            } else {
                p.sendMessage(Component.text("§8§m                                    "));
                p.sendMessage(Component.text("§6📻 Radio Information"));
                p.sendMessage(Component.text("§7Status: §cInactive"));
                p.sendMessage(Component.text("§7No operator assigned"));
                p.sendMessage(Component.text("§7Right-click to configure"));
                p.sendMessage(Component.text("§8§m                                    "));
            }
            plugin.sounds().playClick(p);
            return;
        }
        
        // Normal right-click opens GUI
        plugin.sounds().playClick(p);
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.getRadioMode() == RadioMode.SLIDER) {
                SliderGui.open(plugin, p, r);
            } else {
                RadioGui.open(plugin, p, r);
            }
        });
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof RadioGuiHolder holder)) return;

        e.setCancelled(true);

        Radio r = plugin.store().get(holder.getRadioId());
        if (r == null) {
            p.closeInventory();
            return;
        }

        if (plugin.getRadioMode() == RadioMode.SLIDER) {
            SliderGui.handleClick(plugin, p, r, e);
        } else {
            RadioGui.handleClick(plugin, p, r, e);
        }
    }
}
