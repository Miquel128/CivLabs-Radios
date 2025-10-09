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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RadioGui {

    private static final int ROWS = 3;
    private static final int SIZE = ROWS * 9;

    // page state per player
    private static final Map<UUID, Integer> playerPages = new HashMap<>();

    // plain names (no § codes) so click matching works
    private static final String NAME_PREV = "◀ Previous Page";
    private static final String NAME_NEXT = "Next Page ▶";

    // quick extractors
    private static final Pattern PAT_TX = Pattern.compile("^TX\\s+(\\d+)$");
    private static final Pattern PAT_RX = Pattern.compile("^RX\\s+(\\d+)$");

    /* =========================
       OPEN
       ========================= */
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
            int slot = f - startFreq; // 0..8
            boolean locked = plugin.isFrequencyLockedFor(f, r.getId());
            boolean selected = (r.getTransmitFrequency() == f);
            inv.setItem(slot, txItem(f, locked, selected));
        }

        // Row 2: RX
        for (int f = startFreq; f <= endFreq; f++) {
            int slot = 9 + (f - startFreq); // 9..17
            boolean selected = (r.getListenFrequency() == f);
            inv.setItem(slot, rxItem(f, selected));
        }

        // Row 3: controls
        inv.setItem(18, statusItem(r));
        inv.setItem(22, toggleItem(r.isEnabled()));
        inv.setItem(26, closeItem());

        // Row 3: pagination arrows (plain names!)
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.displayName(Component.text(NAME_PREV));
            meta.lore(List.of(Component.text("Go to page " + page)));
            prev.setItemMeta(meta);
            inv.setItem(19, prev);
        }
        if (page < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.displayName(Component.text(NAME_NEXT));
            meta.lore(List.of(Component.text("Go to page " + (page + 2))));
            next.setItemMeta(meta);
            inv.setItem(25, next);
        }

        viewer.openInventory(inv);
        return inv;
    }

    /* =========================
       CLICK HANDLER
       ========================= */
    public static void handleClick(CivLabsRadiosPlugin plugin, Player p, Radio r, InventoryClickEvent e) {
        e.setCancelled(true); // keep GUI items in place

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.displayName() == null) return;

        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(meta.displayName()).trim();

        // Pagination
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

        // TX select
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

        // RX select
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

        // toggles
        if ("Enable".equals(name)) {
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

    /* =========================
       ITEM FACTORIES
       ========================= */
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
        lore.add(Component.text("§7Status: " + (r.isEnabled() ? "§aON" : "§cOFF")));
        lore.add(Component.text("§7Dimension: §e" + r.getDimension()));
        return named(Material.BOOK, "§6Radio Status", lore);
    }
}
