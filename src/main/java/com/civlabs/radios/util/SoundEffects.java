package com.civlabs.radios.util;

import com.civlabs.radios.CivLabsRadiosPlugin;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

/**
 * Central sound utility with graceful fallbacks.
 * Works even if dbg() is missing in CivLabsRadiosPlugin.
 */
public class SoundEffects {

    private final CivLabsRadiosPlugin plugin;

    public SoundEffects(CivLabsRadiosPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolves a configured sound name to a valid Bukkit Sound enum,
     * falling back to a default if missing or invalid.
     */
    private Sound resolve(String configKey, Sound def) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sounds");
        if (sec == null) return def;

        String soundName = sec.getString(configKey, "");
        if (soundName == null || soundName.isEmpty()) return def;

        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[SoundEffects] Invalid sound name: '" + soundName + "' for key '" + configKey + "'");
            return def;
        }
    }

    /** Plays a generic error/buzz sound. */
    public void playError(Player p) {
        p.playSound(p.getLocation(), resolve("error", Sound.BLOCK_NOTE_BLOCK_BASS), 1f, 0.7f);
    }

    /** Plays when a radio is enabled. */
    public void playEnable(Player p) {
        p.playSound(p.getLocation(), resolve("enable", Sound.BLOCK_NOTE_BLOCK_PLING), 1f, 1.2f);
    }

    /** Plays when a radio is disabled. */
    public void playDisable(Player p) {
        p.playSound(p.getLocation(), resolve("disable", Sound.BLOCK_NOTE_BLOCK_BASS), 1f, 0.6f);
    }

    /** Plays when changing frequency. */
    public void playFrequencyChange(Player p) {
        p.playSound(p.getLocation(), resolve("freqChange", Sound.UI_BUTTON_CLICK), 1f, 1f);
    }

    /** Plays when clicking in a GUI (used by FrequencyOverviewGui). */
    public void playClick(Player p) {
        p.playSound(p.getLocation(), resolve("click", Sound.UI_BUTTON_CLICK), 1f, 1f);
    }
}
