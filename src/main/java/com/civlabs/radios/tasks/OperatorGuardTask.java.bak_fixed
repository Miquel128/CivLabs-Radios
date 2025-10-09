
package com.civlabs.radios.tasks;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.DisableReason;
import com.civlabs.radios.model.Radio;
import com.civlabs.radios.store.RadioStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.function.BiConsumer;

public class OperatorGuardTask {
    private final CivLabsRadiosPlugin plugin;
    private final Runnable task;
    private int taskId = -1;

    public OperatorGuardTask(CivLabsRadiosPlugin plugin, RadioStore store, BiConsumer<Radio, DisableReason> disabler) {
        this.plugin = plugin;
        this.task = () -> {
            int radius = plugin.getConfig().getInt("operatorRadius", 30);
            int radiusSq = radius * radius;

            for (Radio radio : store.getAll()) {
                if (!radio.isEnabled() || radio.getOperator() == null) continue;

                Player p = Bukkit.getPlayer(radio.getOperator());
                Location loc = radio.getLocation();

                if (p == null || !p.isOnline() || loc == null ||
                    !p.getWorld().equals(loc.getWorld()) ||
                    p.getLocation().distanceSquared(loc) > radiusSq) {
                    disabler.accept(radio, DisableReason.OPERATOR_LOST);
                }
            }
        };
    }

    public void start() {
        stop();
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, 40L, 40L);
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
    }
}
