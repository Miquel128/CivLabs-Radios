
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
        this.voice = new VoiceBridge(this);
        this.sounds = new SoundEffects(this);

        this.freqManager.clearAndLoad(radioStore.getAll());

        BukkitVoicechatService svc = getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (svc != null) {
            svc.registerPlugin(new RadioVoicePlugin(this, voice));
        } else {
            getLogger().warning("Simple Voice Chat not detected. Voice features will be disabled.");
        }

        Bukkit.getPluginManager().registerEvents(new RadioPlaceListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RadioInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RadioBreakListener(this), this);

        ItemUtil.registerRecipeIfEnabled(this);

        this.guardTask = new OperatorGuardTask(this, radioStore, this::disableRadioIfEnabled);
        this.guardTask.start();

        log("CivLabsRadios v1.0.3 enabled. Mode=" + currentMode + " MaxFreq=" + getMaxFrequencies() + " Debug=" + debugMode);
    }

    @Override
    public void onDisable() {
        if (guardTask != null) guardTask.stop();
        voice.shutdownAllSpeakers();
        for (Radio r : radioStore.getAll()) {
            if (r.isEnabled()) disableRadioIfEnabled(r, DisableReason.SERVER_STOP);
        }
        log("CivLabsRadios v1.0.3 disabled.");
    }

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

    public RadioStore store() { return radioStore; }
    public FrequencyManager freq() { return freqManager; }
    public VoiceBridge voice() { return voice; }
    public SoundEffects sounds() { return sounds; }
    public boolean isDebug() { return debugMode; }
    public boolean showCoordinates() { return showCoordinates; }
    public RadioMode getRadioMode() { return currentMode; }
    public int getMaxFrequencies() { return freqManager.getMaxFrequencies(); }

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

    public Component msg(String path) {
        String raw = getConfig().getString(path, "");
        return mm.deserialize(raw);
    }

    public void log(String s) { getLogger().info(s); }
    public void dbg(String s) { if (debugMode) getLogger().info("[DEBUG] " + s); }

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
            case "give": {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage("Players only.");
                    return true;
                }
                if (!p.hasPermission("civlabs.radio.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                p.getInventory().addItem(ItemUtil.createRadioItem(this));
                p.sendMessage(mm.deserialize("<green>✓ Gave you a Radio.</green>"));
                return true;
            }
            
            case "list": {
                List<Radio> all = radioStore.getAll();
                sender.sendMessage(mm.deserialize("<gold>═══ Radios (" + all.size() + ") ═══</gold>"));
                for (Radio r : all) {
                    String operatorName = "None";
                    if (r.getOperator() != null) {
                        OfflinePlayer op = Bukkit.getOfflinePlayer(r.getOperator());
                        operatorName = op.getName() != null ? op.getName() : r.getOperator().toString().substring(0, 8);
                    }
                    
                    String coords = showCoordinates ? 
                        String.format(" @ %d,%d,%d", r.getX(), r.getY(), r.getZ()) : "";
                    
                    sender.sendMessage(mm.deserialize(String.format(
                        "<gray>• %s%s [%s] TX:%d RX:%d %s Op:%s</gray>",
                        r.getId().toString().substring(0, 8),
                        coords,
                        r.getDimension(),
                        r.getTransmitFrequency(),
                        r.getListenFrequency(),
                        r.isEnabled() ? "<green>ON</green>" : "<red>OFF</red>",
                        operatorName
                    )));
                }
                return true;
            }
            
            case "free": {
                if (!sender.hasPermission("civlabs.radio.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /radio free <freq>");
                    return true;
                }
                try {
                    int f = Integer.parseInt(args[1]);
                    List<Radio> holders = radioStore.getAll().stream()
                        .filter(r -> r.isEnabled() && r.getTransmitFrequency() == f)
                        .collect(Collectors.toList());
                    
                    if (holders.isEmpty()) {
                        sender.sendMessage(mm.deserialize("<gray>No active transmitter on frequency " + f + ".</gray>"));
                    } else {
                        holders.forEach(r -> disableRadioIfEnabled(r, DisableReason.ADMIN));
                        sender.sendMessage(mm.deserialize("<yellow>✓ Freed frequency " + f + ".</yellow>"));
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage("Invalid frequency number.");
                }
                return true;
            }
            
            case "mode": {
                if (!sender.hasPermission("civlabs.radio.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<gray>Current mode: <yellow>" + currentMode + "</yellow> (" + getMaxFrequencies() + " frequencies)</gray>"));
                    sender.sendMessage(mm.deserialize("<gray>Usage: /radio mode <simple|slider> [maxFreq]</gray>"));
                    return true;
                }
                
                String modeStr = args[1].toLowerCase();
                RadioMode newMode;
                
                try {
                    newMode = RadioMode.valueOf(modeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(mm.deserialize("<red>Invalid mode. Use 'simple' or 'slider'.</red>"));
                    return true;
                }
                
                int maxFreq = 128;
                if (args.length >= 3 && newMode == RadioMode.SLIDER) {
                    try {
                        maxFreq = Integer.parseInt(args[2]);
                        if (maxFreq < 1 || maxFreq > 1024) {
                            sender.sendMessage(mm.deserialize("<red>Max frequency must be between 1 and 1024.</red>"));
                            return true;
                        }
                    } catch (NumberFormatException e) {
                        sender.sendMessage(mm.deserialize("<red>Invalid max frequency number.</red>"));
                        return true;
                    }
                }
                
                setRadioMode(newMode, maxFreq);
                
                String message = getConfig().getString("messages.mode_changed", "");
                if (!message.isEmpty()) {
                    sender.sendMessage(mm.deserialize(message
                        .replace("{mode}", newMode.toString())
                        .replace("{maxFreq}", String.valueOf(getMaxFrequencies()))));
                }
                
                return true;
            }
            
            case "coords": {
                if (!sender.hasPermission("civlabs.radio.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<gray>Coordinates: " + (showCoordinates ? "<green>SHOWN</green>" : "<red>HIDDEN</red>") + "</gray>"));
                    sender.sendMessage(mm.deserialize("<gray>Usage: /radio coords <on|off|toggle></gray>"));
                    return true;
                }
                
                String mode = args[1].toLowerCase(Locale.ROOT);
                if (mode.equals("on")) setShowCoordinates(true);
                else if (mode.equals("off")) setShowCoordinates(false);
                else if (mode.equals("toggle")) setShowCoordinates(!showCoordinates);
                
                sender.sendMessage(mm.deserialize("<gray>Coordinates are now " + (showCoordinates ? "<green>SHOWN</green>" : "<red>HIDDEN</red>") + "</gray>"));
                return true;
            }
            
            case "debug": {
                if (!sender.hasPermission("civlabs.radio.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(mm.deserialize("<gray>Debug: " + (debugMode ? "<green>ON</green>" : "<red>OFF</red>") + "</gray>"));
                    return true;
                }
                
                String mode = args[1].toLowerCase(Locale.ROOT);
                if (mode.equals("on")) setDebug(true);
                else if (mode.equals("off")) setDebug(false);
                else if (mode.equals("toggle")) setDebug(!debugMode);
                
                sender.sendMessage(mm.deserialize("<gray>Debug: " + (debugMode ? "<green>ON</green>" : "<red>OFF</red>") + "</gray>"));
                return true;
            }
            
            case "reload": {
                if (!sender.hasPermission("civlabs.radio.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                reloadConfig();
                loadConfiguration();
                sender.sendMessage(mm.deserialize("<green>✓ Configuration reloaded.</green>"));
                return true;
            }
            
            default:
                sender.sendMessage("Unknown subcommand. Use /radio help");
                return true;
        }
    }

    
    

    

}
