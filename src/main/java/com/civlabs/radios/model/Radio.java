package com.civlabs.radios.model;

import java.util.UUID;

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

    // NEW: fuel in seconds
    private int fuelSeconds;

    // Default empty constructor (used by RadioStore)
    public Radio() {
    }

    // Convenience constructor used by RadioPlaceListener
    public Radio(UUID id, org.bukkit.Location loc, UUID owner) {
        this.id = id;
        if (loc != null && loc.getWorld() != null) {
            this.world = loc.getWorld().getName();
            this.x = loc.getBlockX();
            this.y = loc.getBlockY();
            this.z = loc.getBlockZ();
            this.dimension = loc.getWorld().getEnvironment().name();
        }
        this.owner = owner;
        this.enabled = false;
        this.transmitFrequency = 0;
        this.listenFrequency = 0;
        this.operator = null;
        this.fuelSeconds = 0;
    }

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

    // ----- FUEL -----
    public int getFuelSeconds() { return fuelSeconds; }
    public void setFuelSeconds(int fuelSeconds) { this.fuelSeconds = Math.max(0, fuelSeconds); }
    public void addFuelSeconds(int add) { this.fuelSeconds = Math.max(0, this.fuelSeconds + Math.max(0, add)); }
    public boolean hasFuel() { return fuelSeconds > 0; }

    // Returns the Bukkit Location of this radio (if world exists)
    public org.bukkit.Location getLocation() {
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(this.world);
        if (w == null) return null;
        return new org.bukkit.Location(w, x, y, z);
    }
}
