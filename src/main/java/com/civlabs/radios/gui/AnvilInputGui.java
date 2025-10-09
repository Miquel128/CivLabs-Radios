
package com.civlabs.radios.gui;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.Radio;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AnvilInputGui implements Listener {
    
    private static final Map<UUID, InputSession> sessions = new HashMap<>();
    
    public static void open(CivLabsRadiosPlugin plugin, Player player, Radio radio, boolean isTx) {
        player.sendMessage(Component.text("§e╔═══════════════════════════╗"));
        player.sendMessage(Component.text("§e║ Enter frequency (1-" + plugin.getMaxFrequencies() + ") ║"));
        player.sendMessage(Component.text("§e║ Type 'cancel' to abort   ║"));
        player.sendMessage(Component.text("§e╚═══════════════════════════╝"));
        
        sessions.put(player.getUniqueId(), new InputSession(radio.getId(), isTx));
        player.closeInventory();
    }
    
    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        InputSession session = sessions.get(p.getUniqueId());
        
        if (session == null) return;
        
        e.setCancelled(true);
        sessions.remove(p.getUniqueId());
        
        String input = e.getMessage().trim();
        
        // Prevent blank messages
        if (input.isEmpty()) {
            p.sendMessage(Component.text("§cInput cannot be blank."));
            return;
        }
        
        if (input.equalsIgnoreCase("cancel")) {
            p.sendMessage(Component.text("§cCancelled."));
            return;
        }
        
        try {
            int freq = Integer.parseInt(input);
            CivLabsRadiosPlugin plugin = (CivLabsRadiosPlugin) Bukkit.getPluginManager().getPlugin("CivLabsRadios");
            
            if (plugin == null) {
                p.sendMessage(Component.text("§cPlugin error."));
                return;
            }
            
            if (freq < 1 || freq > plugin.getMaxFrequencies()) {
                p.sendMessage(Component.text("§cFrequency must be between 1 and " + plugin.getMaxFrequencies()));
                return;
            }
            
            Radio r = plugin.store().get(session.radioId);
            if (r == null) {
                p.sendMessage(Component.text("§cRadio not found."));
                return;
            }
            
            if (session.isTx) {
                r.setTransmitFrequency(freq);
                p.sendMessage(Component.text("§a✓ Set TX frequency to " + freq));
            } else {
                r.setListenFrequency(freq);
                plugin.voice().updateSpeakerFor(r);
                p.sendMessage(Component.text("§a✓ Set RX frequency to " + freq));
            }
            
            plugin.store().save(r);
            plugin.sounds().playFrequencyChange(p);
            
            // Reopen GUI
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.getRadioMode() == com.civlabs.radios.core.RadioMode.SLIDER) {
                    SliderGui.open(plugin, p, r);
                } else {
                    RadioGui.open(plugin, p, r);
                }
            });
            
        } catch (NumberFormatException ex) {
            p.sendMessage(Component.text("§cInvalid number. Please enter digits only."));
        }
    }
    
    private static class InputSession {
        final UUID radioId;
        final boolean isTx;
        
        InputSession(UUID radioId, boolean isTx) {
            this.radioId = radioId;
            this.isTx = isTx;
        }
    }
}
