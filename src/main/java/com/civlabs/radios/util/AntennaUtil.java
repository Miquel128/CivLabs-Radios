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
        y++;

        Block block = w.getBlockAt(x, y , z);
        // check if the block on top of the radio is a waxxed_copper_gate
        if(block.getType() != Material.WAXED_COPPER_GRATE)
            return 0;

        y++;
        int count = 0;
        while (true) {
            Block b = w.getBlockAt(x, y, z);
            if (b.getType() != Material.LIGHTNING_ROD)
                break;
            count++;
            y++;
        }

        // check if the antenna is being topped by three waxxed_copper_grate
        for(int i=0; i< 3; i++){
            if( w.getBlockAt(x,y + i,z).getType() != Material.WAXED_COPPER_GRATE)
                return 0;
        }
        
        
        return count;
    }
}
