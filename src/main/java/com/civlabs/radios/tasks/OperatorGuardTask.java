package com.civlabs.radios.tasks;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.DisableReason;
import com.civlabs.radios.model.Radio;
import com.civlabs.radios.store.RadioStore;
import com.civlabs.radios.util.RadioMath;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Ticks every second:
 * - recompute antenna/range (so building changes are reflected)
 * - drain fuel using f(R_final) = 1.002^R_final - 1
 * - auto-disable on empty fuel or missing antennas
 */
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
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Radio r : store.getAll()) {
                if (!r.isEnabled()) continue;

                // Update antenna & range each tick (cheap; ensures live structure)
                RadioMath.recomputeAntennaAndRange(r);

                if (r.getAntennaCount() <= 0 || r.getMaxRangeBlocks() <= 0) {
                    disableFn.accept(r, DisableReason.ADMIN);
                    UUID opId = r.getOperator();
                    Player op = (opId != null ? Bukkit.getPlayer(opId) : null);
                    if (op != null) op.sendMessage(net.kyori.adventure.text.Component.text("§cNo vertical antenna stack found."));
                    continue;
                }

                if (r.getFuelSeconds() > 0) {
                    int burn = RadioMath.burnPerSecond(r.getFinalRangeBlocks()); // ceil(1.002^R - 1)
                    r.setFuelSeconds(r.getFuelSeconds() - Math.max(1, burn));   // always at least 1/s
                    store.save(r);
                }

                if (r.getFuelSeconds() <= 0) {
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
