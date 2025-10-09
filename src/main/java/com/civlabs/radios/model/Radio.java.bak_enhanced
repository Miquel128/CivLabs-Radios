
package com.civlabs.radios.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public class Radio {
    private UUID id;
    private String world;
    private String dimension;
    private int x, y, z;
    private UUID owner;
    private boolean enabled;
    private int transmitFrequency;
    private int listenFrequency;
    private UUID operator;

    public Radio() {}

    public Radio(UUID id, Location loc, UUID owner) {
        this.id = id;
        this.world = loc.getWorld().getName();
        this.dimension = loc.getWorld().getEnvironment().name();
        this.x = loc.getBlockX();
        this.y = loc.getBlockY();
        this.z = loc.getBlockZ();
        this.owner = owner;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getWorld() { return world; }
    public void setWorld(String world) { this.world = world; }

    public String getDimension() { return dimension != null ? dimension : "NORMAL"; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getZ() { return z; }
    public void setZ(int z) { this.z = z; }

    public Location getLocation() {
        World w = Bukkit.getWorld(world);
        return (w == null) ? null : new Location(w, x + 0.5, y + 0.5, z + 0.5);
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getTransmitFrequency() { return transmitFrequency; }
    public void setTransmitFrequency(int transmitFrequency) { this.transmitFrequency = transmitFrequency; }

    public int getListenFrequency() { return listenFrequency; }
    public void setListenFrequency(int listenFrequency) { this.listenFrequency = listenFrequency; }

    public UUID getOperator() { return operator; }
    public void setOperator(UUID operator) { this.operator = operator; }

    public UUID getOwner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
}
