package com.civlabs.radios;

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
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/** Main plugin class for CivLabs Radios. */
public final class CivLabsRadiosPlugin extends JavaPlugin {

    private final MiniMessage mm = MiniMessage.miniMessage();

    private RadioStore radioStore;
    private FrequencyManager freqManager;
    private VoiceBridge voice;
    private OperatorGuardTask guardTask;
    private SoundEffects sounds;
    private RadioMode currentMode;
    private boolean showCoordinates;

    public static NamespacedKey key(String path, JavaPlugin plugin) {
        return new NamespacedKey(plugin, path);
    }

    public Component msg(String path) {
        String raw = getConfig().getString(path, "");
        return mm.deserialize(raw == null ? "" : raw);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Keys.init(this);

        this.currentMode = RadioMode.valueOf(getConfig().getString("radioMode", "simple").toUpperCase());
        this.showCoordinates = getConfig().getBoolean("admin.showCoordinates", false);

        this.radioStore = new RadioStore(getDataFolder().toPath().resolve("radios.yml"));
        this.freqManager = new FrequencyManager(getConfig().getInt("maxFrequencies", 10));
        this.voice = new VoiceBridge(this);
        this.sounds = new SoundEffects(this);

        BukkitVoicechatService svc = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (svc != null) svc.registerPlugin(new RadioVoicePlugin(this, voice));
        else getLogger().warning("Simple Voice Chat not detected as a service.");

        Bukkit.getPluginManager().registerEvents(new RadioPlaceListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RadioInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RadioBreakListener(this), this);

        ItemUtil.registerRecipeIfEnabled(this);

        this.guardTask = new OperatorGuardTask(this, radioStore, this::disableRadioIfEnabled);
        this.guardTask.start();

        getLogger().info("CivLabsRadios enabled.");
    }

    @Override
    public void onDisable() {
        if (guardTask != null) guardTask.stop();
        if (voice != null) voice.shutdownAllSpeakers();
        for (Radio r : radioStore.getAll()) {
            if (r.isEnabled()) disableRadioIfEnabled(r, DisableReason.SERVER_STOP);
        }
        getLogger().info("CivLabsRadios disabled.");
    }
    // Simple debug helper used by listeners/util classes
    public void dbg(String msg) {
        if (getConfig().getBoolean("debug.enabled", false)) {
            getLogger().info("[DEBUG] " + msg);
        }
    }

    // Accessors
    public RadioStore store() { return radioStore; }
    public FrequencyManager freq() { return freqManager; }
    public VoiceBridge voice() { return voice; }
    public SoundEffects sounds() { return sounds; }
    public int getMaxFrequencies() { return freqManager.getMaxFrequencies(); }
    public RadioMode getRadioMode() { return currentMode; }
    public boolean showCoordinates() { return showCoordinates; }

    // Frequency lock check used in GUI
    public boolean isFrequencyLockedFor(int f, UUID requesterRadioId) {
        for (Radio r : radioStore.getAll()) {
            if (r.isEnabled() && r.getTransmitFrequency() == f && !r.getId().equals(requesterRadioId)) {
                return true;
            }
        }
        return false;
    }

    public boolean canOperate(Player p) { return true; }

    public boolean enableRadio(Radio r, Player operator, int txFreq) {
        if (!canOperate(operator)) return false;

        // require fuel
        if (r.getFuelSeconds() <= 0) {
            operator.sendMessage(Component.text("Radio has no fuel. Insert copper (ingots/blocks) into the orange slot."));
            sounds().playError(operator);
            return false;
        }

        // require antennas/range recomputed
        com.civlabs.radios.util.RadioMath.recomputeAntennaAndRange(r);
        if (r.getAntennaCount() <= 0 || r.getMaxRangeBlocks() <= 0) {
            operator.sendMessage(Component.text("§cNo vertical antenna stack found."));
            sounds().playError(operator);
            return false;
        }

        if (!freqManager.claim(txFreq, r.getId())) {
            operator.sendMessage(
                    msg("messages.freq_in_use")
                            .replaceText(b -> b.matchLiteral("{freq}").replacement(String.valueOf(txFreq)))
            );
            return false;
        }

        r.setTransmitFrequency(txFreq);
        r.setOperator(operator.getUniqueId());
        r.setEnabled(true);
        radioStore.save(r);

        voice.bindOperator(r, operator);
        operator.sendMessage(
                msg("messages.enabled")
                        .replaceText(b -> b.matchLiteral("{freq}").replacement(String.valueOf(txFreq)))
        );
        return true;
    }

    public void disableRadioIfEnabled(Radio r, DisableReason reason) {
        if (!r.isEnabled()) return;

        if (r.getTransmitFrequency() > 0) freqManager.release(r.getTransmitFrequency(), r.getId());

        UUID op = r.getOperator();
        if (op != null) voice.unbindOperator(op, r.getTransmitFrequency());

        voice.removeSpeaker(r.getId());
        r.setEnabled(false);
        r.setOperator(null);
        radioStore.save(r);

        Player notify = op != null ? Bukkit.getPlayer(op) : null;
        if (notify != null) {
            notify.sendMessage(
                    msg("messages.disabled")
                            .replaceText(b -> b.matchLiteral("{reason}").replacement(reason.name()))
            );
        }
    }

    // /radio (subset)
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("radio")) return false;

        if (args.length == 0) {
            sender.sendMessage("Use /radio give | /radio mode <simple|slider> [maxFreq] | /radio reload");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "give" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                if (!p.hasPermission("civlabs.radio.admin")) { p.sendMessage(Component.text("No permission")); return true; }
                ItemStack radio = ItemUtil.createRadioItem(this);
                p.getInventory().addItem(radio);
                p.sendMessage(Component.text("Gave you a Radio!"));
                sounds.playFrequencyChange(p);
                return true;
            }
            case "mode" -> {
                if (!sender.hasPermission("civlabs.radio.admin")) { sender.sendMessage("No permission"); return true; }
                if (args.length < 2) { sender.sendMessage("Usage: /radio mode <simple|slider> [maxFreq]"); return true; }
                boolean slider = switch (args[1].toLowerCase()) { case "simple" -> false; case "slider" -> true; default -> { sender.sendMessage("Invalid mode. Use simple|slider."); yield false; } };
                int maxFreq = getConfig().getInt("maxFrequencies", 10);
                if (slider && args.length >= 3) {
                    try { maxFreq = Math.max(1, Math.min(1024, Integer.parseInt(args[2]))); }
                    catch (NumberFormatException e) { sender.sendMessage("Invalid max frequency."); return true; }
                } else if (!slider) {
                    maxFreq = getConfig().getInt("simpleFrequencies", 9);
                }
                getConfig().set("maxFrequencies", maxFreq);
                saveConfig();
                this.freqManager = new FrequencyManager(maxFreq);
                for (Radio r : radioStore.getAll()) {
                    if (r.isEnabled() && r.getTransmitFrequency() > maxFreq) disableRadioIfEnabled(r, DisableReason.ADMIN);
                }
                sender.sendMessage("Mode set to " + (slider ? "slider" : "simple") + " with max " + maxFreq + ".");
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("civlabs.radio.admin")) { sender.sendMessage("No permission"); return true; }
                reloadConfig();
                int maxFreq = getConfig().getInt("maxFrequencies", freqManager.getMaxFrequencies());
                this.freqManager = new FrequencyManager(maxFreq);
                for (Radio r : radioStore.getAll()) {
                    if (r.isEnabled() && r.getTransmitFrequency() > maxFreq) disableRadioIfEnabled(r, DisableReason.ADMIN);
                }
                sender.sendMessage("Configuration reloaded.");
                return true;
            }
            default -> {
                sender.sendMessage("Unknown subcommand. Use /radio give | /radio mode | /radio reload");
                return true;
            }
        }
    }
}
