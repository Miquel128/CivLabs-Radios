package com.civlabs.radios.gui;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.core.RadioMode;
import com.civlabs.radios.model.Radio;
import com.civlabs.radios.util.RadioMath;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Radio GUI with:
 * - Pagination (TX/RX rows)
 * - Range selector (1..5) and info card
 * - Fuel slot (copper ingot/block to add fuel)
 * - Stash slot
 * - Live "Fuel remaining" (ingots) + lifetime "Added total" + Burn (ingots/s)
 */
public class RadioGui {

    // Pretty number formats
    private static final DecimalFormat DF2 = new DecimalFormat("#,##0.##");
    private static final DecimalFormat DF3 = new DecimalFormat("#,##0.###");

    // Fuel burn rate (ingots per second) using f(x) = 1.002^x − 1, x = final range in blocks
    private static double fuelPerSecond(Radio r) {
        int range = Math.max(0, r.getFinalRangeBlocks());
        return Math.pow(1.002, range) - 1.0;
    }

    /** Holder to identify our GUI. */
    public static class RadioGuiHolder implements InventoryHolder {
        private final UUID radioId;
        public RadioGuiHolder(UUID id) { this.radioId = id; }
        public UUID getRadioId() { return radioId; }
        @Override public Inventory getInventory() { return null; }
    }

    private static final int ROWS = 3;
    private static final int SIZE = ROWS * 9;

    // Row 3 controls
    private static final int SLOT_STATUS = 18;
    private static final int SLOT_PREV   = 19;
    private static final int SLOT_RANGE  = 20;
    private static final int SLOT_FUEL   = 21;
    private static final int SLOT_TOGGLE = 22;
    private static final int SLOT_STASH  = 23;
    private static final int SLOT_INFO   = 24;
    private static final int SLOT_NEXT   = 25;
    private static final int SLOT_CLOSE  = 26;

    // Per-viewer page and live status updater
    private static final Map<UUID, Integer> playerPages = new HashMap<>();
    private static final Map<UUID, BukkitTask> statusUpdaters = new HashMap<>();

    /* =========================
       OPEN
       ========================= */
    public static Inventory open(CivLabsRadiosPlugin plugin, Player viewer, Radio r) {
        if (plugin.getRadioMode() == RadioMode.SLIDER) {
            // If you have a dedicated SliderGui, you could delegate here.
        }
        int currentPage = playerPages.getOrDefault(viewer.getUniqueId(), 0);
        Inventory inv = open(plugin, viewer, r, currentPage);

        // Start/refresh live status updater (1 Hz)
        startFuelStatusUpdater(plugin, viewer, r);
        return inv;
    }

    public static Inventory open(CivLabsRadiosPlugin plugin, Player viewer, Radio r, int page) {
        int maxFreq = plugin.getMaxFrequencies();
        int totalPages = Math.max(1, (int) Math.ceil(maxFreq / 9.0));
        page = Math.max(0, Math.min(page, totalPages - 1));
        playerPages.put(viewer.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(new RadioGuiHolder(r.getId()), SIZE,
                Component.text("Radio").color(getDimensionColor(r.getDimension())));

        // Update antenna/range snapshot for info card
        RadioMath.recomputeAntennaAndRange(r);
        plugin.store().save(r);

        // Frequencies for this page
        int startFreq = page * 9 + 1;
        int endFreq = Math.min(startFreq + 8, maxFreq);

        // Row 1: TX
        for (int f = startFreq; f <= endFreq; f++) {
            int slot = f - startFreq;
            boolean locked = plugin.isFrequencyLockedFor(f, r.getId());
            boolean selected = (r.getTransmitFrequency() == f);
            inv.setItem(slot, txItem(f, locked, selected));
        }
        // Row 2: RX
        for (int f = startFreq; f <= endFreq; f++) {
            int slot = 9 + (f - startFreq);
            boolean selected = (r.getListenFrequency() == f);
            inv.setItem(slot, rxItem(f, selected));
        }

        // Row 3 controls
        inv.setItem(SLOT_STATUS, statusItem(r));
        inv.setItem(SLOT_TOGGLE, toggleItem(r.isEnabled()));
        inv.setItem(SLOT_CLOSE, closeItem());
        if (page > 0) inv.setItem(SLOT_PREV, pageArrow("§e◀ Previous Page", "§7Go to page " + page));
        if (page < totalPages - 1) inv.setItem(SLOT_NEXT, pageArrow("§eNext Page ▶", "§7Go to page " + (page + 2)));

        // Range & info
        inv.setItem(SLOT_RANGE, rangeItem(r.getRangeStep(), r.getFinalRangeBlocks(), r.getMaxRangeBlocks()));
        inv.setItem(SLOT_INFO,  infoItem(r));

        // Stash/Fuel placeholders if empty
        if (isEmpty(inv.getItem(SLOT_STASH))) {
            inv.setItem(SLOT_STASH, pane(Material.LIME_STAINED_GLASS_PANE, "§aStash Slot",
                    List.of(Component.text("§7Place any item here (no effect yet)"))));
        }
        if (isEmpty(inv.getItem(SLOT_FUEL))) {
            inv.setItem(SLOT_FUEL, pane(Material.ORANGE_STAINED_GLASS_PANE, "§6Fuel Slot",
                    List.of(Component.text("§7Place Copper Ingot/Block to add fuel"))));
        }

        viewer.openInventory(inv);
        return inv;
    }

    /* =========================
       LIVE STATUS UPDATER
       ========================= */
    private static void startFuelStatusUpdater(CivLabsRadiosPlugin plugin, Player viewer, Radio r) {
        // Cancel previous if any
        BukkitTask prev = statusUpdaters.remove(viewer.getUniqueId());
        if (prev != null) prev.cancel();

        // Start a new 1 Hz task
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Only update if this viewer still has our GUI open
            if (viewer == null || !viewer.isOnline()) {
                stopFuelStatusUpdater(viewer);
                return;
            }
            Inventory top = viewer.getOpenInventory() != null ? viewer.getOpenInventory().getTopInventory() : null;
            if (top == null || !(top.getHolder() instanceof RadioGuiHolder h) || !h.getRadioId().equals(r.getId())) {
                stopFuelStatusUpdater(viewer);
                return;
            }
            // Refresh just the status item (remaining + total)
            top.setItem(SLOT_STATUS, statusItem(r));
        }, 0L, 20L);

        statusUpdaters.put(viewer.getUniqueId(), task);
    }

    private static void stopFuelStatusUpdater(Player viewer) {
        if (viewer == null) return;
        BukkitTask t = statusUpdaters.remove(viewer.getUniqueId());
        if (t != null) t.cancel();
    }

    /** Call this from InventoryCloseEvent in your listener. */
    public static void onClose(Player viewer, Inventory top) {
        if (viewer == null || top == null) return;
        if (top.getHolder() instanceof RadioGuiHolder) {
            stopFuelStatusUpdater(viewer);
        }
    }

    /* =========================
       CLICK HANDLING
       ========================= */
    public static void handleClick(CivLabsRadiosPlugin plugin, Player p, Radio r, InventoryClickEvent e) {
        if (e.getCurrentItem() == null) return;

        // Safe display name
        String name = (e.getCurrentItem().hasItemMeta() && e.getCurrentItem().getItemMeta().hasDisplayName())
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(e.getCurrentItem().getItemMeta().displayName())
                : "";

        int raw = e.getRawSlot();
        int topSize = e.getView().getTopInventory().getSize();
        boolean inTop = raw >= 0 && raw < topSize;

        // Pagination
        if (inTop && raw == SLOT_PREV) {
            e.setCancelled(true);
            int currentPage = playerPages.getOrDefault(p.getUniqueId(), 0);
            open(plugin, p, r, currentPage - 1);
            plugin.sounds().playClick(p);
            return;
        }
        if (inTop && raw == SLOT_NEXT) {
            e.setCancelled(true);
            int currentPage = playerPages.getOrDefault(p.getUniqueId(), 0);
            open(plugin, p, r, currentPage + 1);
            plugin.sounds().playClick(p);
            return;
        }

        // Range
        if (inTop && raw == SLOT_RANGE) {
            e.setCancelled(true);
            int step = r.getRangeStep();
            if (e.isLeftClick()) step = Math.min(5, step + 1);
            else if (e.isRightClick()) step = Math.max(1, step - 1);
            r.setRangeStep(step);
            plugin.store().save(r);
            // refresh visuals
            e.getInventory().setItem(SLOT_RANGE, rangeItem(r.getRangeStep(), r.getFinalRangeBlocks(), r.getMaxRangeBlocks()));
            e.getInventory().setItem(SLOT_INFO, infoItem(r));
            plugin.sounds().playClick(p);
            return;
        }

        // Info (force recompute)
        if (inTop && raw == SLOT_INFO) {
            e.setCancelled(true);
            RadioMath.recomputeAntennaAndRange(r);
            plugin.store().save(r);
            e.getInventory().setItem(SLOT_INFO, infoItem(r));
            e.getInventory().setItem(SLOT_RANGE, rangeItem(r.getRangeStep(), r.getFinalRangeBlocks(), r.getMaxRangeBlocks()));
            plugin.sounds().playClick(p);
            return;
        }

        // Fuel slot (click-based add)
        if (inTop && raw == SLOT_FUEL) {
            ItemStack cursor = e.getCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                // Picking up from slot; block placeholder removal
                ItemStack current = e.getCurrentItem();
                if (current != null && current.getType() == Material.ORANGE_STAINED_GLASS_PANE) {
                    e.setCancelled(true);
                } else {
                    // allow pickup; restore placeholder if empty next tick
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        ItemStack now = e.getInventory().getItem(SLOT_FUEL);
                        if (isEmpty(now)) {
                            e.getInventory().setItem(SLOT_FUEL, pane(Material.ORANGE_STAINED_GLASS_PANE, "§6Fuel Slot",
                                    List.of(Component.text("§7Place Copper Ingot/Block to add fuel"))));
                        }
                    }, 1L);
                }
                return;
            }

            Material t = cursor.getType();
            if (t == Material.COPPER_INGOT || t == Material.COPPER_BLOCK) {
                e.setCancelled(true);
                int amount = cursor.getAmount();
                int secs = (t == Material.COPPER_INGOT) ? 3 * amount : 27 * amount;
                r.addFuelSeconds(secs);
                r.addTotalFuelAddedSeconds(secs); // lifetime counter
                plugin.store().save(r);
                // consume cursor stack
                cursor.setAmount(0);
                p.setItemOnCursor(cursor);
                // refresh status
                updateStatusItem(e.getInventory(), r);
                plugin.sounds().playClick(p);
            } else {
                e.setCancelled(true);
                plugin.sounds().playError(p);
            }
            return;
        }

        // Stash slot
        if (inTop && raw == SLOT_STASH) {
            ItemStack cursor = e.getCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                // picking up item unless it's placeholder
                ItemStack in = e.getCurrentItem();
                if (in != null && in.getType() == Material.LIME_STAINED_GLASS_PANE) {
                    e.setCancelled(true);
                } else {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        ItemStack now = e.getInventory().getItem(SLOT_STASH);
                        if (isEmpty(now)) {
                            e.getInventory().setItem(SLOT_STASH, pane(Material.LIME_STAINED_GLASS_PANE, "§aStash Slot",
                                    List.of(Component.text("§7Place any item here (no effect yet)"))));
                        }
                    }, 1L);
                }
            } else {
                // let Bukkit do the swap; we ensure placeholder returns if emptied later
                Bukkit.getScheduler().runTaskLater(plugin, () -> {}, 1L);
            }
            return;
        }

        // TX / RX
        if (name.startsWith("TX ")) {
            e.setCancelled(true);
            int f = Integer.parseInt(name.substring(3));
            if (plugin.isFrequencyLockedFor(f, r.getId())) {
                p.sendMessage(plugin.msg("messages.freq_in_use")
                        .replaceText(b -> b.matchLiteral("{freq}").replacement(String.valueOf(f))));
                plugin.sounds().playError(p);
                return;
            }
            r.setTransmitFrequency(f);
            plugin.store().save(r);
            plugin.sounds().playFrequencyChange(p);
            open(plugin, p, r, playerPages.getOrDefault(p.getUniqueId(), 0));
            return;
        }

        if (name.startsWith("RX ")) {
            e.setCancelled(true);
            int f = Integer.parseInt(name.substring(3));
            r.setListenFrequency(f);
            plugin.store().save(r);
            plugin.voice().updateSpeakerFor(r);
            plugin.sounds().playFrequencyChange(p);
            open(plugin, p, r, playerPages.getOrDefault(p.getUniqueId(), 0));
            return;
        }

        // Enable / Disable
        if ("Enable".equals(name)) {
            e.setCancelled(true);
            if (r.getTransmitFrequency() > 0) {
                plugin.enableRadio(r, p, r.getTransmitFrequency());
                updateStatusItem(e.getInventory(), r);
                e.getInventory().setItem(SLOT_TOGGLE, toggleItem(r.isEnabled()));
            }
            return;
        }

        if ("Disable".equals(name)) {
            e.setCancelled(true);
            plugin.disableRadioIfEnabled(r, com.civlabs.radios.model.DisableReason.ADMIN);
            updateStatusItem(e.getInventory(), r);
            e.getInventory().setItem(SLOT_TOGGLE, toggleItem(r.isEnabled()));
            return;
        }

        if ("Close".equals(name)) {
            e.setCancelled(true);
            p.closeInventory();
        }
    }

    /* =========================
       DRAG HANDLING
       ========================= */
    public static void handleDrag(CivLabsRadiosPlugin plugin, Player p, Radio r, InventoryDragEvent e) {
        int topSize = e.getView().getTopInventory().getSize();
        Set<Integer> rawSlots = e.getRawSlots();
        boolean touchesTop = rawSlots.stream().anyMatch(s -> s < topSize);
        if (!touchesTop) return;

        // Only allow single-slot drags (to our special slots)
        if (rawSlots.size() != 1) {
            e.setCancelled(true);
            return;
        }

        int slot = rawSlots.iterator().next();

        // Fuel drag
        if (slot == SLOT_FUEL) {
            ItemStack cursor = e.getOldCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                e.setCancelled(true);
                return;
            }

            Material t = cursor.getType();
            if (t != Material.COPPER_INGOT && t != Material.COPPER_BLOCK) {
                e.setCancelled(true);
                plugin.sounds().playError(p);
                return;
            }

            ItemStack newItem = e.getNewItems().getOrDefault(slot, null);
            int placed = (newItem == null ? 0 : newItem.getAmount());
            if (placed <= 0) {
                e.setCancelled(true);
                return;
            }

            int secs = (t == Material.COPPER_INGOT) ? 3 * placed : 27 * placed;
            r.addFuelSeconds(secs);
            r.addTotalFuelAddedSeconds(secs); // lifetime counter
            plugin.store().save(r);

            e.setCancelled(true);
            ItemStack newCursor = cursor.clone();
            newCursor.setAmount(Math.max(0, cursor.getAmount() - placed));

            Bukkit.getScheduler().runTask(plugin, () -> {
                // keep placeholder
                e.getView().getTopInventory().setItem(SLOT_FUEL,
                        pane(Material.ORANGE_STAINED_GLASS_PANE, "§6Fuel Slot",
                                List.of(Component.text("§7Place Copper Ingot/Block to add fuel"))));
                updateStatusItem(e.getView().getTopInventory(), r);
                e.getView().setCursor(newCursor);
                plugin.sounds().playClick(p);
            });
            return;
        }

        // Stash drag
        if (slot == SLOT_STASH) {
            ItemStack cursor = e.getOldCursor();
            if (cursor == null || cursor.getType() == Material.AIR ||
                    cursor.getType() == Material.LIME_STAINED_GLASS_PANE) {
                e.setCancelled(true);
                return;
            }

            ItemStack newItem = e.getNewItems().getOrDefault(slot, null);
            int placed = (newItem == null ? 0 : newItem.getAmount());
            if (placed <= 0) {
                e.setCancelled(true);
                return;
            }

            e.setCancelled(true);
            ItemStack newCursor = cursor.clone();
            newCursor.setAmount(Math.max(0, cursor.getAmount() - placed));
            ItemStack stashStack = cursor.clone();
            stashStack.setAmount(placed);

            Bukkit.getScheduler().runTask(plugin, () -> {
                e.getView().getTopInventory().setItem(SLOT_STASH, stashStack);
                e.getView().setCursor(newCursor);
                plugin.sounds().playClick(p);
            });
            return;
        }

        // Block all other drags into top inventory
        e.setCancelled(true);
    }

    /* =========================
       HELPERS
       ========================= */
    private static TextColor getDimensionColor(String dimension) {
        return switch (dimension) {
            case "NETHER" -> TextColor.color(200, 50, 50);
            case "THE_END" -> TextColor.color(180, 100, 200);
            default -> TextColor.color(255, 255, 255);
        };
    }

    private static boolean isEmpty(ItemStack it) {
        return it == null || it.getType() == Material.AIR;
    }

    private static ItemStack named(Material m, String name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name));
        if (lore != null && !lore.isEmpty()) meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack pane(Material m, String name, List<Component> lore) {
        return named(m, name, lore);
    }

    private static ItemStack txItem(int f, boolean locked, boolean selected) {
        if (locked) {
            return named(Material.BARRIER, "TX " + f, List.of(Component.text("§cIn use by another radio")));
        } else if (selected) {
            return named(Material.REDSTONE_TORCH, "TX " + f, List.of(Component.text("§aCurrently selected")));
        } else {
            return named(Material.STONE_BUTTON, "TX " + f, List.of(Component.text("§7Click to transmit on " + f)));
        }
    }

    private static ItemStack rxItem(int f, boolean selected) {
        if (selected) {
            return named(Material.NOTE_BLOCK, "RX " + f, List.of(Component.text("§aListening to " + f)));
        } else {
            return named(Material.OAK_BUTTON, "RX " + f, List.of(Component.text("§7Click to listen to " + f)));
        }
    }

    private static ItemStack statusItem(Radio r) {
        // Convert internal seconds to ingots (1 ingot = 3s)
        double fuelIngots = r.getFuelSeconds() / 3.0;
        double burn = fuelPerSecond(r); // ingots per second

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7TX: §e" + (r.getTransmitFrequency() > 0 ? r.getTransmitFrequency() : "None")));
        lore.add(Component.text("§7RX: §e" + (r.getListenFrequency() > 0 ? r.getListenFrequency() : "None")));
        lore.add(Component.text("§7Status: " + (r.isEnabled() ? "§aON" : "§cOFF")));
        lore.add(Component.text("§7Dimension: §e" + r.getDimension()));
        lore.add(Component.text("§7Fuel remaining: §e" + DF2.format(fuelIngots) + " ingots"));
        lore.add(Component.text("§7Added total: §e" + r.getTotalFuelAddedSeconds() + "s"));
        lore.add(Component.text("§7Burn: §e" + DF3.format(burn) + " ingots/s"));
        return named(Material.BOOK, "§6Radio Status", lore);
    }

    private static void updateStatusItem(Inventory inv, Radio r) {
        inv.setItem(SLOT_STATUS, statusItem(r));
    }

    private static ItemStack toggleItem(boolean on) {
        return named(on ? Material.REDSTONE_TORCH : Material.LEVER,
                on ? "Disable" : "Enable",
                List.of(Component.text(on ? "§cClick to turn off" : "§aClick to turn on")));
    }

    private static ItemStack closeItem() {
        return named(Material.BARRIER, "Close", List.of(Component.text("§7Close this menu")));
    }

    private static ItemStack pageArrow(String name, String lore) {
        return named(Material.ARROW, name, List.of(Component.text(lore)));
    }

    private static ItemStack rangeItem(int step, int finalRange, int maxRange) {
        return named(Material.REPEATER, "Range: " + step + "/5",
                List.of(
                        Component.text("§7Select working range"),
                        Component.text("§7Final: §e" + finalRange + " §7/ Max: §e" + maxRange + " §7blocks"),
                        Component.text("§8Left-click: +1  |  Right-click: -1")
                ));
    }

    private static ItemStack infoItem(Radio r) {
        return named(Material.PAPER, "§6Antenna / Height",
                List.of(
                        Component.text("§7Antennas (rods): §e" + r.getAntennaCount()),
                        Component.text("§7Y: §e" + r.getY()),
                        Component.text("§7Max range: §e" + r.getMaxRangeBlocks() + " blocks"),
                        Component.text("§7Selected: §e" + r.getFinalRangeBlocks() + " blocks")
                ));
    }
}
