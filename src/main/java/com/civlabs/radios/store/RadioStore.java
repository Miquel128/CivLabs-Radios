package com.civlabs.radios.store;

import com.civlabs.radios.model.Radio;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class RadioStore {

    private final File file;
    private final Map<UUID, Radio> radios = new HashMap<>();

    public RadioStore(Path path) {
        this.file = path.toFile();
        load();
    }

    public synchronized void delete(UUID id) {
        radios.remove(id);
        saveAll();
    }

    public synchronized void save(Radio r) {
        radios.put(r.getId(), r);
        saveAll();
    }
    
    public synchronized Radio get(UUID id) { return radios.get(id); }

    public synchronized List<Radio> getAll() {
        return new ArrayList<>(radios.values());
    }
    // find the first radio at Location loc
    public synchronized Optional<Radio> byLocation(Location loc) {
        return radios.values().stream().filter(r ->
                r.getWorld().equals(loc.getWorld().getName()) &&
                        r.getX() == loc.getBlockX() && r.getY() == loc.getBlockY() && r.getZ() == loc.getBlockZ()
        ).findFirst();
    }
    // find the first radio operated by op
    public synchronized Optional<Radio> byOperator(UUID op) {
        return radios.values().stream().filter(r -> op.equals(r.getOperator())).findFirst();
    }

    public synchronized List<Radio> listenersOn(int freq) {
        return radios.values().stream()
                .filter(r -> r.getListenFrequency() == freq)
                .collect(Collectors.toList());
    }

    private void load() {
        radios.clear();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            UUID id = UUID.fromString(key);
            Radio r = new Radio();
            r.setId(id);
            r.setWorld(cfg.getString(key + ".world"));
            r.setX(cfg.getInt(key + ".x"));
            r.setY(cfg.getInt(key + ".y"));
            r.setZ(cfg.getInt(key + ".z"));
            r.setDimension(cfg.getString(key + ".dimension", "NORMAL"));
            String owner = cfg.getString(key + ".owner");
            r.setOwner(owner == null ? null : UUID.fromString(owner));
            r.setEnabled(cfg.getBoolean(key + ".enabled"));
            r.setTransmitFrequency(cfg.getInt(key + ".tx"));
            r.setListenFrequency(cfg.getInt(key + ".rx"));
            String op = cfg.getString(key + ".operator");
            r.setOperator(op == null ? null : UUID.fromString(op));
            r.setFuelSeconds(cfg.getInt(key + ".fuel", 0));

            // NEW persisted fields
            r.setAntennaCount(cfg.getInt(key + ".antenna", 0));
            r.setMaxRangeBlocks(cfg.getInt(key + ".maxRange", 0));
            r.setRangeStep(cfg.getInt(key + ".rangeStep", 5));
            r.setTotalFuelAddedSeconds(cfg.getInt(key + ".fuelTotal", 0));

            radios.put(id, r);
        }
    }

    private void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Radio r : radios.values()) {
            String k = r.getId().toString();
            cfg.set(k + ".world", r.getWorld());
            cfg.set(k + ".x", r.getX());
            cfg.set(k + ".y", r.getY());
            cfg.set(k + ".z", r.getZ());
            cfg.set(k + ".dimension", r.getDimension());
            cfg.set(k + ".owner", r.getOwner() == null ? null : r.getOwner().toString());
            cfg.set(k + ".enabled", r.isEnabled());
            cfg.set(k + ".tx", r.getTransmitFrequency());
            cfg.set(k + ".rx", r.getListenFrequency());
            cfg.set(k + ".operator", r.getOperator() == null ? null : r.getOperator().toString());
            cfg.set(k + ".fuel", r.getFuelSeconds());

            // NEW persisted fields
            cfg.set(k + ".antenna", r.getAntennaCount());
            cfg.set(k + ".maxRange", r.getMaxRangeBlocks());
            cfg.set(k + ".rangeStep", r.getRangeStep());
            cfg.set(k + ".fuelTotal", r.getTotalFuelAddedSeconds());
        }
        try { cfg.save(file); } catch (IOException e) { e.printStackTrace(); }
    }
}
