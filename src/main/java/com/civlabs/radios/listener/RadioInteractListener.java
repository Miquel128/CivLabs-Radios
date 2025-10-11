package com.civlabs.radios.listener;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.gui.RadioGui;
import com.civlabs.radios.model.Radio;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;

import java.util.Set;

public class RadioInteractListener implements Listener {

    private final CivLabsRadiosPlugin plugin;

    public RadioInteractListener(CivLabsRadiosPlugin plugin) {
        this.plugin = plugin;
    }

    /* =========================
       OPEN RADIO GUI ON RIGHT-CLICK
       ========================= */
    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;

        Block b = e.getClickedBlock();

        // We rely on stored location → radio mapping, so no hard Material check needed.
        // If you want to optimize, you could check for Material.SMOKER here.

        plugin.store().byLocation(b.getLocation()).ifPresent(r -> {
            // Cancel default container opening and show our GUI
            e.setCancelled(true);

            Player p = e.getPlayer();
            RadioGui.open(plugin, p, r);
            plugin.sounds().playClick(p);
        });
    }

    /* =========================
       GUI CLICK HANDLING
       ========================= */
    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        // Only handle clicks for our Radio GUI
        InventoryHolder topHolder = e.getView().getTopInventory().getHolder();
        if (!(topHolder instanceof RadioGui.RadioGuiHolder holder)) return;

        // Cancel top inv by default; allow specific actions inside RadioGui.handleClick
        int topSize = e.getView().getTopInventory().getSize();
        boolean inTop = e.getRawSlot() >= 0 && e.getRawSlot() < topSize;
        if (inTop) {
            e.setCancelled(true);
        } else if (e.isShiftClick()) {
            e.setCancelled(true);
        }

        Radio r = plugin.store().get(holder.getRadioId());
        if (r == null) {
            p.closeInventory();
            return;
        }

        RadioGui.handleClick(plugin, p, r, e);
    }

    /* =========================
       GUI DRAG HANDLING
       ========================= */
    @EventHandler
    public void onInvDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        InventoryHolder topHolder = e.getView().getTopInventory().getHolder();
        if (!(topHolder instanceof RadioGui.RadioGuiHolder holder)) return;

        int topSize = e.getView().getTopInventory().getSize();
        Set<Integer> raw = e.getRawSlots();
        boolean touchesTop = raw.stream().anyMatch(s -> s < topSize);
        if (!touchesTop) return;

        // Cancel by default; RadioGui.handleDrag will apply allowed moves (fuel/stash)
        e.setCancelled(true);

        Radio r = plugin.store().get(holder.getRadioId());
        if (r == null) {
            p.closeInventory();
            return;
        }

        RadioGui.handleDrag(plugin, p, r, e);
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        RadioGui.onClose(p, e.getInventory());
    }
}
