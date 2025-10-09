
package com.civlabs.radios.util;

import com.civlabs.radios.CivLabsRadiosPlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundEffects {
    private final CivLabsRadiosPlugin plugin;
    private final boolean enabled;

    public SoundEffects(CivLabsRadiosPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("sounds.enabled", true);
    }

    private void play(Player p, String configKey) {
        if (!enabled || p == null) return;
        
        try {
            String soundName = plugin.getConfig().getString("sounds." + configKey, "UI_BUTTON_CLICK");
            Sound sound = Sound.valueOf(soundName);
            p.playSound(p.getLocation(), sound, 0.7f, 1.0f);
        } catch (Exception e) {
            plugin.dbg("Invalid sound: " + configKey);
        }
    }

    public void playFrequencyChange(Player p) {
        play(p, "frequencyChange");
    }

    public void playEnable(Player p) {
        play(p, "enable");
    }

    public void playDisable(Player p) {
        play(p, "disable");
    }

    public void playError(Player p) {
        play(p, "error");
    }

    public void playClick(Player p) {
        play(p, "click");
    }
}
