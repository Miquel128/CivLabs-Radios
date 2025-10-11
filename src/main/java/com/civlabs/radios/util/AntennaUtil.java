package com.civlabs.radios.util;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Vertical-only lightning-rod scanning (strictly above the radio block). */
public final class AntennaUtil {
    private AntennaUtil() {}

    public static int countVerticalRodsAbove(Location radioLoc) {
        if (radioLoc == null) return 0;
        World w = radioLoc.getWorld();
        if (w == null) return 0;
        int x = radioLoc.getBlockX();
        int y = radioLoc.getBlockY();
        int z = radioLoc.getBlockZ();

        int count = 0;
        while (true) {
            Block b = w.getBlockAt(x, y + 1 + count, z);
            if (b.getType() == Material.LIGHTNING_ROD) count++;
            else break;
        }
        return count;
    }
}
