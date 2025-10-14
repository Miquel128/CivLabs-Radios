package com.civlabs.radios.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Radio data model — includes:
 * - Fuel tracking (remaining seconds)
 * - Lifetime fuel accumulation (totalFuelAddedSeconds)
 * - Antenna / range info (rangeStep 1..5, derived final range)
 */
public class Radio {

    private UUID id;
    private String world;
    private int x, y, z;
    private String dimension = "NORMAL";
    private UUID owner;
    private boolean enabled;
    private int transmitFrequency;
    private int listenFrequency;
    private UUID operator;

    // --- Fuel ---
    private double fuelSeconds;              // Remaining seconds of fuel

    // --- Antenna / Range ---
    private int antennaCount;
    private int maxRangeBlocks;
    private int rangeStep = 1; // 1..5 (final = rangeStep / 5 * maxRangeBlocks)

    public Radio() {}

    public Radio(UUID id, Location loc, UUID owner) {
        this.id = id;
        if (loc != null && loc.getWorld() != null) {
            this.world = loc.getWorld().getName();
            this.x = loc.getBlockX();
            this.y = loc.getBlockY();
            this.z = loc.getBlockZ();
            this.dimension = loc.getWorld().getEnvironment().name();
        }
        this.owner = owner;
    }

    // --- Basic info ---
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getZ() { return z; }
    public void setZ(int z) { this.z = z; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getTransmitFrequency() { return transmitFrequency; }
    public void setTransmitFrequency(int transmitFrequency) { this.transmitFrequency = transmitFrequency; }

    public int getListenFrequency() { return listenFrequency; }
    public void setListenFrequency(int listenFrequency) { this.listenFrequency = listenFrequency; }

    public UUID getOperator() { return operator; }
    public void setOperator(UUID operator) { this.operator = operator; }

    // --- Fuel ---
    public double getFuelSeconds() { return fuelSeconds; }
    public void setFuelSeconds(double fuelSeconds) {
        this.fuelSeconds = Math.max(0, fuelSeconds);
    }
    public void addFuelSeconds(int add) {
        this.fuelSeconds = Math.max(0, this.fuelSeconds + Math.max(0, add));
    }
    public boolean hasFuel() { return fuelSeconds > 0; }



    // --- Range / antenna ---
    public int getAntennaCount() { return antennaCount; }
    public void setAntennaCount(int antennaCount) { this.antennaCount = Math.max(0, antennaCount); }

    public int getMaxRangeBlocks() { return maxRangeBlocks; }
    public void setMaxRangeBlocks(int maxRangeBlocks) { this.maxRangeBlocks = Math.max(0, maxRangeBlocks); }

    public int getRangeStep() { return rangeStep; }
    public void setRangeStep(int rangeStep) { this.rangeStep = Math.min(5, Math.max(1, rangeStep)); }

    /** Final selected range in blocks = (rangeStep/5) * maxRangeBlocks */
    public int getFinalRangeBlocks() {
        return (int) Math.floor((getRangeStep() / 5.0) * getMaxRangeBlocks());
    }

    public Location getLocation() {
        World w = Bukkit.getWorld(this.world);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }
}
