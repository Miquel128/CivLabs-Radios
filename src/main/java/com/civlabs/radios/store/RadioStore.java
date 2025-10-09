
package com.civlabs.radios.store;

import com.civlabs.radios.model.Radio;
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

    public synchronized void create(Radio r) {
        radios.put(r.getId(), r);
        saveAll();
    }

    public synchronized void delete(UUID id) {
        radios.remove(id);
        saveAll();
    }

    public synchronized void save(Radio r) {
        radios.put(r.getId(), r);
        saveAll();
    }
    public Optional<Radio> byOperator(UUID operatorId) {
        if (operatorId == null) return Optional.empty();
        return getAll().stream()
                .filter(r -> operatorId.equals(r.getOperator()))
                .findFirst();
    }


    public synchronized Radio get(UUID id) {
        return radios.get(id);
    }

    public synchronized List<Radio> getAll() {
        return new ArrayList<>(radios.values());
    }

    public synchronized List<Radio> listenersOn(int freq) {
        return radios.values().stream()
            .filter(r -> r.getListenFrequency() == freq)
            .collect(Collectors.toList());
    }

    private void load() {
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                Radio r = new Radio();
                r.setId(id);
                r.setWorld(cfg.getString(key + ".world"));
                r.setDimension(cfg.getString(key + ".dimension", "NORMAL"));
                r.setX(cfg.getInt(key + ".x"));
                r.setY(cfg.getInt(key + ".y"));
                r.setZ(cfg.getInt(key + ".z"));
                r.setEnabled(cfg.getBoolean(key + ".enabled", false));
                r.setTransmitFrequency(cfg.getInt(key + ".tx", 0));
                r.setListenFrequency(cfg.getInt(key + ".rx", 0));

                String ownerStr = cfg.getString(key + ".owner");
                if (ownerStr != null) r.setOwner(UUID.fromString(ownerStr));

                String opStr = cfg.getString(key + ".operator");
                if (opStr != null) r.setOperator(UUID.fromString(opStr));

                radios.put(id, r);
            } catch (Exception ignored) {}
        }
    }

    private void saveAll() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Radio r : radios.values()) {
            String k = r.getId().toString();
            cfg.set(k + ".world", r.getWorld());
            cfg.set(k + ".dimension", r.getDimension());
            cfg.set(k + ".x", r.getX());
            cfg.set(k + ".y", r.getY());
            cfg.set(k + ".z", r.getZ());
            cfg.set(k + ".enabled", r.isEnabled());
            cfg.set(k + ".tx", r.getTransmitFrequency());
            cfg.set(k + ".rx", r.getListenFrequency());
            if (r.getOwner() != null) cfg.set(k + ".owner", r.getOwner().toString());
            if (r.getOperator() != null) cfg.set(k + ".operator", r.getOperator().toString());
        }

        try {
            cfg.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
