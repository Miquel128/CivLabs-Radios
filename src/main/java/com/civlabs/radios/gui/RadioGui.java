package com.civlabs.radios.gui;

import com.civlabs.radios.CivLabsRadiosPlugin;
import com.civlabs.radios.core.RadioMode;
import com.civlabs.radios.model.Radio;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RadioGui {

    private static final int ROWS = 3;
    private static final int SIZE = ROWS * 9;

    private static final Map<UUID, Integer> playerPages = new HashMap<>();

    // green stash (index 1); orange no longer stores items
    private static final Map<UUID, ItemStack[]> radioStash = new HashMap<>();

    private static final int SLOT_STATUS = 18;
    private static final int SLOT_PREV   = 19;
    private static final int SLOT_LEFT_FUEL   = 21; // ORANGE fuel slot (consumes copper)
    private static final int SLOT_TOGGLE = 22;
    private static final int SLOT_RIGHT_STASH = 23; // GREEN stash slot
    private static final int SLOT_NEXT   = 25;
    private static final int SLOT_CLOSE  = 26;

    private static final String NAME_PREV = "◀ Previous Page";
    private static final String NAME_NEXT = "Next Page ▶";

    private static final Pattern PAT_TX = Pattern.compile("^TX\\s+(\\d+)$");
    private static final Pattern PAT_RX = Pattern.compile("^RX\\s+(\\d+)$");

    public static Inventory open(CivLabsRadiosPlugin plugin, Player viewer, Radio r) {
        if (plugin.getRadioMode() == RadioMode.SLIDER) {
            SliderGui.open(plugin, viewer, r);
            return null;
        }
        int currentPage = playerPages.getOrDefault(viewer.getUniqueId(), 0);
        return open(plugin, viewer, r, currentPage);
    }

    public static Inventory open(CivLabsRadiosPlugin plugin, Player viewer, Radio r, int page) {
        int maxFreq = plugin.getMaxFrequencies();

        int totalPages = Math.max(1, (int) Math.ceil(maxFreq / 9.0));
        page = Math.max(0, Math.min(page, totalPages - 1));
        playerPages.put(viewer.getUniqueId(), page);

        Inventory inv = Bukkit.createInventory(
                new RadioGuiHolder(r.getId()),
                SIZE,
                Component.text("Radio").color(getDimensionColor(r.getDimension()))
        );

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

        // Row 3: controls
        inv.setItem(SLOT_STATUS, statusItem(r));                 // shows Fuel too
        inv.setItem(SLOT_TOGGLE, toggleItem(r.isEnabled()));
        inv.setItem(SLOT_CLOSE, closeItem());

        // Orange: always show pane (fuel input)
        inv.setItem(SLOT_LEFT_FUEL, placeholderPane(false));
        // Green: stash (shows stored item if present)
        ItemStack[] stash = radioStash.computeIfAbsent(r.getId(), id -> new ItemStack[2]);
        inv.setItem(SLOT_RIGHT_STASH, stash[1] != null ? stash[1].clone() : placeholderPane(true));

        // Pagination arrows
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.displayName(Component.text(NAME_PREV));
            meta.lore(List.of(Component.text("Go to page " + page)));
            prev.setItemMeta(meta);
            inv.setItem(SLOT_PREV, prev);
        }
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.displayName(Component.text(NAME_NEXT));
            meta.lore(List.of(Component.text("Go to page " + (page + 2))));
            next.setItemMeta(meta);
            inv.setItem(SLOT_NEXT, next);
        }

        viewer.openInventory(inv);
        return inv;
    }

    public static void handleClick(CivLabsRadiosPlugin plugin, Player p, Radio r, InventoryClickEvent e) {
        boolean topInv = e.getClickedInventory() != null && e.getClickedInventory().getHolder() instanceof RadioGuiHolder;
        int rawSlot = e.getRawSlot();

        // Block shift-clicks from player inv into GUI
        if (!topInv && e.isShiftClick()) {
            e.setCancelled(true);
            return;
        }
        // Allow normal movement in player inv
        if (!topInv) {
            e.setCancelled(false);
            return;
        }

        // Top inv
        boolean isFuelSlot  = (rawSlot == SLOT_LEFT_FUEL);
        boolean isStashSlot = (rawSlot == SLOT_RIGHT_STASH);

        if (isFuelSlot) {
            e.setCancelled(true);

            ItemStack cursor = e.getCursor();
            if (cursor == null || cursor.getType() == Material.AIR) {
                // empty click on fuel slot does nothing
                return;
            }

            Material t = cursor.getType();
            int amount = cursor.getAmount();
            int perItemSeconds = 0;

            if (t == Material.COPPER_INGOT) {
                perItemSeconds = 3; // 3 sec per ingot
            } else if (t == Material.COPPER_BLOCK) {
                perItemSeconds = 27; // 27 sec per block
            } else {
                plugin.sounds().playError(p);
                p.sendMessage(Component.text("Fuel slot accepts only Copper Ingots or Copper Blocks."));
                return;
            }

            int add = perItemSeconds * Math.max(1, amount);
            r.addFuelSeconds(add);
            plugin.store().save(r);

            // consume from cursor
            p.setItemOnCursor(null);

            // refresh status to show new fuel
            e.getInventory().setItem(SLOT_STATUS, statusItem(r));
            plugin.sounds().playFrequencyChange(p);
            return;
        }

        if (isStashSlot) {
            // Green stash: deposit/retrieve
            e.setCancelled(true);
            ItemStack[] stash = radioStash.computeIfAbsent(r.getId(), id -> new ItemStack[2]);
            int idx = 1; // right slot

            ItemStack cursor = e.getCursor();
            boolean cursorHasItem = cursor != null && cursor.getType() != Material.AIR;

            if (cursorHasItem) {
                if (stash[idx] == null) {
                    stash[idx] = cursor.clone();
                    p.setItemOnCursor(null);
                    e.getInventory().setItem(rawSlot, stash[idx].clone());
                    plugin.sounds().playFrequencyChange(p);
                } else {
                    plugin.sounds().playError(p);
                }
            } else {
                if (stash[idx] != null) {
                    p.setItemOnCursor(stash[idx].clone());
                    stash[idx] = null;
                    e.getInventory().setItem(rawSlot, placeholderPane(true));
                    plugin.sounds().playFrequencyChange(p);
                }
            }
            return;
        }

        // GUI slots handled by names below
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.displayName() == null) return;

        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName()).trim();

        if (NAME_PREV.equals(name)) {
            int currentPage = playerPages.getOrDefault(p.getUniqueId(), 0);
            open(plugin, p, r, currentPage - 1);
            return;
        }
        if (NAME_NEXT.equals(name)) {
            int currentPage = playerPages.getOrDefault(p.getUniqueId(), 0);
            open(plugin, p, r, currentPage + 1);
            return;
        }

        Matcher mTx = PAT_TX.matcher(name);
        if (mTx.matches()) {
            int f = Integer.parseInt(mTx.group(1));
            if (plugin.isFrequencyLockedFor(f, r.getId())) {
                p.sendMessage(plugin.msg("messages.freq_in_use")
                        .replaceText(b -> b.matchLiteral("{freq}").replacement(String.valueOf(f))));
                plugin.sounds().playError(p);
                return;
            }
            r.setTransmitFrequency(f);
            plugin.store().save(r);
            plugin.sounds().playFrequencyChange(p);
            open(plugin, p, r);
            return;
        }

        Matcher mRx = PAT_RX.matcher(name);
        if (mRx.matches()) {
            int f = Integer.parseInt(mRx.group(1));
            r.setListenFrequency(f);
            plugin.store().save(r);
            plugin.voice().updateSpeakerFor(r);
            plugin.sounds().playFrequencyChange(p);
            open(plugin, p, r);
            return;
        }

        if ("Enable".equals(name)) {
            if (r.getFuelSeconds() <= 0) {
                p.sendMessage(Component.text("Radio has no fuel. Insert copper into the orange slot."));
                plugin.sounds().playError(p);
                return;
            }
            if (r.getTransmitFrequency() > 0) {
                plugin.enableRadio(r, p, r.getTransmitFrequency());
            }
            open(plugin, p, r);
            return;
        }
        if ("Disable".equals(name)) {
            plugin.disableRadioIfEnabled(r, com.civlabs.radios.model.DisableReason.ADMIN);
            open(plugin, p, r);
            return;
        }
        if ("Close".equals(name)) {
            p.closeInventory();
        }
    }

    public static void handleDrag(CivLabsRadiosPlugin plugin, Player p, Radio r, InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof RadioGuiHolder) {
            e.setCancelled(true);
        }
    }

    private static TextColor getDimensionColor(String dimension) {
        return switch (dimension) {
            case "NETHER" -> TextColor.color(200, 50, 50);
            case "THE_END" -> TextColor.color(180, 100, 200);
            default -> TextColor.color(255, 255, 255);
        };
    }

    private static ItemStack named(Material m, String name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name));
        if (lore != null && !lore.isEmpty()) meta.lore(lore);
        it.setItemMeta(meta);
        return it;
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

    private static ItemStack toggleItem(boolean on) {
        return named(
                on ? Material.REDSTONE_TORCH : Material.LEVER,
                on ? "Disable" : "Enable",
                List.of(Component.text(on ? "§cClick to turn off" : "§aClick to turn on"))
        );
    }

    private static ItemStack closeItem() {
        return named(Material.BARRIER, "Close", List.of(Component.text("§7Close this menu")));
    }

    private static ItemStack statusItem(Radio r) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7TX: §e" + (r.getTransmitFrequency() > 0 ? r.getTransmitFrequency() : "None")));
        lore.add(Component.text("§7RX: §e" + (r.getListenFrequency() > 0 ? r.getListenFrequency() : "None")));
        lore.add(Component.text("§7Fuel: §e" + r.getFuelSeconds() + "s"));
        lore.add(Component.text("§7Status: " + (r.isEnabled() ? "§aON" : "§cOFF")));
        lore.add(Component.text("§7Dimension: §e" + r.getDimension()));
        return named(Material.BOOK, "§6Radio Status", lore);
    }

    private static ItemStack placeholderPane(boolean green) {
        Material mat = green ? Material.LIME_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
        String name = green ? "Green Slot" : "Orange Slot";
        return named(mat, name, List.of(Component.text(green ? "§7Storage slot" : "§7Fuel: copper ingots/blocks")));
    }
}
