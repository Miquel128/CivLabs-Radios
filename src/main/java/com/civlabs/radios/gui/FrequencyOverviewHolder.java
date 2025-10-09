
package com.civlabs.radios.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public class FrequencyOverviewHolder implements InventoryHolder {
    private final UUID radioId;

    public FrequencyOverviewHolder(UUID radioId) {
        this.radioId = radioId;
    }

    public UUID getRadioId() {
        return radioId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}
