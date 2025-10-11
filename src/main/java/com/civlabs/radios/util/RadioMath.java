package com.civlabs.radios.util;

import com.civlabs.radios.model.Radio;
import org.bukkit.Location;

/** Range math helpers for radios. */
public final class RadioMath {
    private RadioMath() {}

    /** Recompute antennaCount and maxRangeBlocks from current world state and Y. */
    public static void recomputeAntennaAndRange(Radio r) {
        Location loc = r.getLocation();
        int a = AntennaUtil.countVerticalRodsAbove(loc);
        int b = (loc != null ? loc.getBlockY() : r.getY());

        // R_total = sqrt(30000*a) + 4.687*b - 200, clamped >= 0
        double partA = Math.sqrt(30000.0 * a);
        double partB = 4.687 * b - 200.0;
        int total = (int) Math.max(0, Math.floor(partA + partB));

        r.setAntennaCount(a);
        r.setMaxRangeBlocks(total);
    }

    /** Fuel burn per second using f(x) = 1.002^x - 1, x = R_final */
    public static int burnPerSecond(int finalRangeBlocks) {
        if (finalRangeBlocks <= 0) return 0;
        double burn = Math.pow(1.002, finalRangeBlocks) - 1.0; // exponential
        return (int) Math.ceil(burn); // consume whole seconds of fuel
    }
}
