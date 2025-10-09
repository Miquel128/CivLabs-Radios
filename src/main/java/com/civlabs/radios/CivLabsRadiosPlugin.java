package com.civlabs.radios;

import com.civlabs.radios.command.RadioTabCompleter;
import com.civlabs.radios.core.FrequencyManager;
import com.civlabs.radios.core.RadioMode;
import com.civlabs.radios.listener.RadioBreakListener;
import com.civlabs.radios.listener.RadioInteractListener;
import com.civlabs.radios.listener.RadioPlaceListener;
import com.civlabs.radios.model.DisableReason;
import com.civlabs.radios.model.Radio;
import com.civlabs.radios.store.RadioStore;
import com.civlabs.radios.tasks.OperatorGuardTask;
import com.civlabs.radios.util.ItemUtil;
import com.civlabs.radios.util.Keys;
import com.civlabs.radios.util.SoundEffects;
import com.civlabs.radios.voice.RadioVoicePlugin;
import com.civlabs.radios.voice.VoiceBridge;
import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main plugin class for CivLabs Radios.
 * Integrates with Simple Voice Chat through VoiceBridge (which handles delay, static, etc.)
 */
public final class CivLabsRadiosPlugin extends JavaPlugin {

    private RadioStore radioStore;
    private FrequencyManager freqManager;
    private VoiceBridge voice;
    private OperatorGuardTask guardTask;
    private SoundEffects sounds;

    private boolean debugMode;
    private boolean showCoordinates;
    private RadioMode currentMode;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public static NamespacedKey key(String path, JavaPlugin plugin) {
        return new NamespacedKey(plugin, path);
    }

    @Override
    public void onEnable() {
        if (getCommand("radio") != null) getCommand("radio").setTabCompleter(new RadioTabCompleter(this));
        saveDefaultConfig();
        Keys.init(this);

        loadConfiguration();

        this.radioStore = new RadioStore(getDataFolder().toPath().resolve("radios.yml"));
        this.freqManager = new FrequencyManager(getMaxFrequenciesForMode());
        this.voice = new VoiceBridge(this); // <<=== main voice bridge
        this.sounds = new SoundEffects(this);

        this.freqManager.clearAndLoad(radioStore.getAll());

        // ---- Simple Voice Chat integration ----
        BukkitVoicechatService svc = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (svc != null) {
            svc.registerPlugin(new RadioVoicePlugin(this, voice));
            getLogger().info("[CivLabsRadios] Registered VoiceBridge with Simple Voice Chat.");
        } else {
            getLogger().warning("[CivLabsRadios] Simple Voice Chat not detected. Voice features will be disabled.");
        }
        // ---------------------------------------

        // Register listeners and recipes
        Bukkit.getPluginManager().registerEvents(new RadioPlaceListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RadioInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RadioBreakListener(this), this);

        ItemUtil.registerRecipeIfEnabled(this);

        // Operator guard task (monitors active radios)
        this.guardTask = new OperatorGuardTask(this, radioStore, this::disableRadioIfEnabled);
        this.guardTask.start();

        log("CivLabsRadios v1.0.3 enabled. Mode=" + currentMode + " MaxFreq=" + getMaxFrequencies() + " Debug=" + debugMode);
    }

    @Override
    public void onDisable() {
        if (guardTask != null) guardTask.stop();

        // shut down voice channels/speakers cleanly
        if (voice != null) {
            voice.shutdownAllSpeakers();
        }

        for (Radio r : radioStore.getAll()) {
            if (r.isEnabled()) disableRadioIfEnabled(r, DisableReason.SERVER_STOP);
        }
        log("CivLabsRadios v1.0.3 disabled.");
    }

    /* =========================
       CONFIG / STATE MANAGEMENT
       ========================= */
    private void loadConfiguration() {
        this.debugMode = getConfig().getBoolean("debug.enabled", false);
        this.showCoordinates = getConfig().getBoolean("admin.showCoordinates", false);

        String modeStr = getConfig().getString("radioMode", "simple");
        this.currentMode = RadioMode.valueOf(modeStr.toUpperCase());
    }

    private int getMaxFrequenciesForMode() {
        return currentMode == RadioMode.SIMPLE ?
                getConfig().getInt("simpleFrequencies", 9) :
                Math.min(getConfig().getInt("maxFrequencies", 128), 1024);
    }

    /* =========================
       ACCESSORS
       ========================= */
    public RadioStore store() { return radioStore; }
    public FrequencyManager freq() { return freqManager; }
    public VoiceBridge voice() { return voice; }
    public SoundEffects sounds() { return sounds; }
    public boolean isDebug() { return debugMode; }
    public boolean showCoordinates() { return showCoordinates; }
    public RadioMode getRadioMode() { return currentMode; }
    public int getMaxFrequencies() { return freqManager.getMaxFrequencies(); }

    /* =========================
       CONFIG MUTATORS
       ========================= */
    public void setDebug(boolean on) {
        this.debugMode = on;
        getConfig().set("debug.enabled", on);
        saveConfig();
    }

    public void setShowCoordinates(boolean show) {
        this.showCoordinates = show;
        getConfig().set("admin.showCoordinates", show);
        saveConfig();
    }

    public void setRadioMode(RadioMode mode, int maxFreq) {
        this.currentMode = mode;
        getConfig().set("radioMode", mode.name().toLowerCase());

        if (mode == RadioMode.SLIDER) {
            maxFreq = Math.min(Math.max(maxFreq, 1), 1024);
            getConfig().set("maxFrequencies", maxFreq);
        } else {
            maxFreq = getConfig().getInt("simpleFrequencies", 9);
        }

        saveConfig();

        for (Radio r : radioStore.getAll()) {
            if (r.isEnabled() && r.getTransmitFrequency() > maxFreq) {
                disableRadioIfEnabled(r, DisableReason.ADMIN);
            }
        }

        this.freqManager = new FrequencyManager(maxFreq);
        this.freqManager.clearAndLoad(radioStore.getAll());
    }

    /* =========================
       RADIO MANAGEMENT
       ========================= */
    public boolean canOperate(Player p) {
        return true;
    }

    public boolean enableRadio(Radio r, Player operator, int txFreq) {
        if (!canOperate(operator)) return false;

        if (getConfig().getBoolean("dimensions.restrictToDimension", true)) {
            if (!r.getDimension().equals(operator.getWorld().getEnvironment().name())) {
                operator.sendMessage(msg("messages.dimension_mismatch")
                        .replaceText(b -> b.matchLiteral("{dimension}").replacement(r.getDimension()))
                        .replaceText(b -> b.matchLiteral("{current}").replacement(operator.getWorld().getEnvironment().name())));
                sounds.playError(operator);
                return false;
            }
        }

        if (txFreq > freqManager.getMaxFrequencies()) {
            operator.sendMessage(msg("messages.freq_out_of_range")
                    .replaceText(b -> b.matchLiteral("{freq}").replacement(String.valueOf(txFreq))));
            sounds.playError(operator);
            return false;
        }

        if (isFrequencyLockedFor(txFreq, r.getId())) {
            operator.sendMessage(msg("messages.freq_in_use")
                    .replaceText(b -> b.matchLiteral("{freq}").replacement(String.valueOf(txFreq))));
            sounds.playError(operator);
            return false;
        }

        if (!freqManager.claim(txFreq, r.getId())) {
            operator.sendMessage(msg("messages.freq_in_use")
                    .replaceText(b -> b.matchLiteral("{freq}").replacement(String.valueOf(txFreq))));
            sounds.playError(operator);
            return false;
        }

        r.setTransmitFrequency(txFreq);
        r.setOperator(operator.getUniqueId());
        r.setEnabled(true);
        radioStore.save(r);

        freqManager.clearAndLoad(radioStore.getAll());

        // Bind operator to TX group in VoiceChat
        voice.bindOperator(r, operator);

        operator.sendMessage(msg("messages.enabled")
                .replaceText(b -> b.matchLiteral("{freq}").replacement(String.valueOf(txFreq))));
        sounds.playEnable(operator);
        return true;
    }

    public void disableRadioIfEnabled(Radio r, DisableReason reason) {
        if (!r.isEnabled()) return;

        if (r.getTransmitFrequency() > 0) {
            freqManager.release(r.getTransmitFrequency(), r.getId());
        }

        UUID op = r.getOperator();
        if (op != null) voice.unbindOperator(op, r.getTransmitFrequency());

        voice.removeSpeaker(r.getId());
        r.setEnabled(false);
        r.setOperator(null);
        radioStore.save(r);

        freqManager.clearAndLoad(radioStore.getAll());

        Player notify = op != null ? Bukkit.getPlayer(op) : null;
        if (notify != null) {
            notify.sendMessage(msg("messages.disabled")
                    .replaceText(b -> b.matchLiteral("{reason}").replacement(reason.name())));
            sounds.playDisable(notify);
        }
    }

    public boolean isFrequencyLockedFor(int f, UUID requesterRadioId) {
        for (Radio r : radioStore.getAll()) {
            if (r.isEnabled() && r.getTransmitFrequency() == f && !r.getId().equals(requesterRadioId)) {
                return true;
            }
        }
        return false;
    }

    /* =========================
       UTILS
       ========================= */
    public Component msg(String path) {
        String raw = getConfig().getString(path, "");
        return mm.deserialize(raw);
    }

    public void log(String s) { getLogger().info(s); }
    public void dbg(String s) { if (debugMode) getLogger().info("[DEBUG] " + s); }

    /* =========================
       COMMANDS (/radio)
       ========================= */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("radio")) return false;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(mm.deserialize("<gold>═══ CivLabsRadios v1.0.3 ═══</gold>"));
            sender.sendMessage(mm.deserialize("<yellow>/radio help</yellow> <gray>- Show this help</gray>"));
            sender.sendMessage(mm.deserialize("<yellow>/radio give</yellow> <gray>- Get a Radio item (admin)</gray>"));
            sender.sendMessage(mm.deserialize("<yellow>/radio list</yellow> <gray>- List all radios</gray>"));
            sender.sendMessage(mm.deserialize("<yellow>/radio free <freq></yellow> <gray>- Force free a frequency (admin)</gray>"));
            sender.sendMessage(mm.deserialize("<yellow>/radio mode <simple|slider> [maxFreq]</yellow> <gray>- Change radio mode (admin)</gray>"));
            sender.sendMessage(mm.deserialize("<yellow>/radio coords <on|off|toggle></yellow> <gray>- Toggle coordinate display (admin)</gray>"));
            sender.sendMessage(mm.deserialize("<yellow>/radio debug <on|off|toggle></yellow> <gray>- Toggle debug mode (admin)</gray>"));
            sender.sendMessage(mm.deserialize("<yellow>/radio reload</yellow> <gray>- Reload configuration (admin)</gray>"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "give" -> { /* unchanged */ }
            case "list" -> { /* unchanged */ }
            case "free" -> { /* unchanged */ }
            case "mode" -> { /* unchanged */ }
            case "coords" -> { /* unchanged */ }
            case "debug" -> { /* unchanged */ }
            case "reload" -> { /* unchanged */ }
            default -> sender.sendMessage("Unknown subcommand. Use /radio help");
        }
        return true;
    }
}
