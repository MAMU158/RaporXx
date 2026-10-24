package com.raporx.gui;

import com.raporx.Messages;
import com.raporx.RaporX;
import com.raporx.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Tüm menülerin ortak tabanı. Tıklamalar slot -> işlem tablosuyla yönetilir. */
public abstract class Menu implements InventoryHolder {
    protected final RaporX plugin;
    protected final Player viewer;
    protected Inventory inventory;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();

    protected Menu(RaporX plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
    }

    protected abstract String title();

    protected abstract int rows();

    protected abstract void build();

    /** Boş slotlar cam ile doldurulsun mu. */
    protected boolean fillEmpty() {
        return true;
    }

    public void open() {
        inventory = Bukkit.createInventory(this, rows() * 9, title());
        build();
        decorate();
        viewer.openInventory(inventory);
    }

    public void refresh() {
        handlers.clear();
        inventory.clear();
        build();
        decorate();
    }

    private void decorate() {
        if (!fillEmpty()) {
            return;
        }
        ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    protected Messages msgs() {
        return plugin.messages();
    }

    protected void set(int slot, ItemStack item, Consumer<InventoryClickEvent> handler) {
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, item);
        if (handler != null) {
            handlers.put(slot, handler);
        } else {
            handlers.remove(slot);
        }
    }

    protected void set(int slot, ItemStack item) {
        set(slot, item, null);
    }

    /** İsim ve açıklaması messages.yml'de {key}.name / {key}.lore olan düğme. */
    protected ItemStack button(Material material, String key, String... kv) {
        return new ItemBuilder(material)
                .name(msgs().ui(key + ".name", kv))
                .lore(msgs().uiList(key + ".lore", kv))
                .build();
    }

    protected void addClose(int slot) {
        set(slot, button(Material.BARRIER, "menu.common.close"), e -> viewer.closeInventory());
    }

    protected void addBack(int slot, Runnable action) {
        set(slot, button(Material.ARROW, "menu.common.back"), e -> action.run());
    }

    protected void playSound(String key) {
        viewer.playSound(viewer.getLocation(), key, 0.6f, 1.2f);
    }

    protected static String dash(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }

    public void click(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> h = handlers.get(event.getSlot());
        if (h != null) {
            playSound("ui.button.click");
            h.accept(event);
        }
    }

    public void closed(InventoryCloseEvent event) {
        // alt sınıflar gerekirse override eder
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
