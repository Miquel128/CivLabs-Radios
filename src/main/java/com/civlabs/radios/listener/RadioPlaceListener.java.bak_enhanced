
package com.civlabs.radios.listener;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.model.Radio;
import com.civlabs.radios.util.ItemUtil;
import com.civlabs.radios.util.Keys;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class RadioPlaceListener implements Listener {
    private final CivLabsRadiosPlugin plugin;

    public RadioPlaceListener(CivLabsRadiosPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!ItemUtil.isRadioItem(e.getItemInHand())) return;

        Block b = e.getBlockPlaced();
        if (!(b.getState() instanceof TileState ts)) {
            plugin.getLogger().warning("Radio block '" + b.getType() + "' is not a TileEntity!");
            return;
        }

        UUID id = UUID.randomUUID();
        ts.getPersistentDataContainer().set(Keys.RADIO_ID, PersistentDataType.STRING, id.toString());
        ts.update();

        Radio r = new Radio(id, b.getLocation(), e.getPlayer().getUniqueId());
        plugin.store().create(r);
        plugin.sounds().playClick(e.getPlayer());
        
        plugin.dbg("Placed radio " + id + " at " + b.getLocation() + " in " + r.getDimension());
    }
}
