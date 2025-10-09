package com.civlabs.radios.command;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.Radio;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

public class RadioTabCompleter implements TabCompleter {

    private final CivLabsRadiosPlugin plugin;

    public RadioTabCompleter(CivLabsRadiosPlugin plugin) {
        this.plugin = plugin;
    }

    private List<String> prefix(List<String> options, String typed) {
        String t = typed == null ? "" : typed.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : options) {
            if (t.isEmpty() || s.toLowerCase(Locale.ROOT).startsWith(t)) out.add(s);
        }
        Collections.sort(out);
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("radio")) return Collections.emptyList();

        if (args.length == 0 || args.length == 1) {
            return prefix(Arrays.asList("help", "give", "list", "free", "mode", "coords", "debug", "reload"),
                    args.length == 0 ? "" : args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "mode": {
                if (args.length == 2) {
                    return prefix(Arrays.asList("simple", "slider"), args[1]);
                } else if (args.length == 3 && "slider".equalsIgnoreCase(args[1])) {
                    return prefix(Arrays.asList("128", "256", "512", "1024"), args[2]);
                }
                return Collections.emptyList();
            }
            case "coords":
            case "debug": {
                if (args.length == 2) {
                    return prefix(Arrays.asList("on", "off", "toggle"), args[1]);
                }
                return Collections.emptyList();
            }
            case "free": {
                if (args.length == 2) {
                    Set<Integer> occupied = new TreeSet<>();
                    for (Radio r : plugin.store().getAll()) {
                        if (r.isEnabled() && r.getTransmitFrequency() >= 1) occupied.add(r.getTransmitFrequency());
                    }
                    List<String> opts = new ArrayList<>();
                    for (Integer i : occupied) opts.add(String.valueOf(i));
                    if (opts.isEmpty()) opts.add("<freq>");
                    return prefix(opts, args[1]);
                }
                return Collections.emptyList();
            }
            default:
                return Collections.emptyList();
        }
    }
}