package com.civlabs.radios.tasks;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.DisableReason;
import com.civlabs.radios.model.Radio;
import com.civlabs.radios.store.RadioStore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.function.BiConsumer;

public class OperatorGuardTask {

    private final CivLabsRadiosPlugin plugin;
    private final RadioStore store;
    private final BiConsumer<Radio, DisableReason> disableFn;
    private BukkitTask task;

    public OperatorGuardTask(CivLabsRadiosPlugin plugin, RadioStore store, BiConsumer<Radio, DisableReason> disableFn) {
        this.plugin = plugin;
        this.store = store;
        this.disableFn = disableFn;
    }

    public void start() {
        stop();
        // run every 20 ticks (1s)
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Radio r : store.getAll()) {
                if (!r.isEnabled()) continue;

                // Tick fuel down while enabled (simple baseline).
                if (r.getFuelSeconds() > 0) {
                    r.setFuelSeconds(r.getFuelSeconds() - 1);
                    store.save(r);
                }

                if (r.getFuelSeconds() <= 0) {
                    // Out of fuel -> disable
                    disableFn.accept(r, DisableReason.FUEL);
                }
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
