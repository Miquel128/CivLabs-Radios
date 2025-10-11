package com.civlabs.radios.listener;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.DisableReason;
import com.civlabs.radios.model.Radio;
import com.civlabs.radios.util.ItemUtil;
import com.civlabs.radios.util.Keys;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class RadioBreakListener implements Listener {
    private final CivLabsRadiosPlugin plugin;

    public RadioBreakListener(CivLabsRadiosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!(b.getState() instanceof TileState ts)) return;

        String tag = ts.getPersistentDataContainer().get(Keys.RADIO_ID, PersistentDataType.STRING);
        if (tag == null) return;

        UUID id = UUID.fromString(tag);
        Radio r = plugin.store().get(id);
        if (r == null) return;

        plugin.disableRadioIfEnabled(r, DisableReason.BLOCK_BROKEN);
        plugin.store().delete(id);

        if (plugin.getConfig().getBoolean("allowItemDropOnBreak", true)) {
            e.setDropItems(false);
            b.getWorld().dropItemNaturally(b.getLocation(), ItemUtil.createRadioItem(plugin));
        }

        plugin.sounds().playClick(e.getPlayer());
    }
}
