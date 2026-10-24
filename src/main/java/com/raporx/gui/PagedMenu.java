package com.raporx.gui;

import com.raporx.RaporX;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Sayfalı liste menüsü. Üst 5 satır içerik, alt satır:
 * 45 önceki | 46 geri | 47-48 ekstra | 49 kapat | 50-51 ekstra | 52 sayfa | 53 sonraki
 */
public abstract class PagedMenu<T> extends Menu {
    private static final int PAGE_SIZE = 45;

    protected final List<T> items;
    protected int page = 0;

    protected PagedMenu(RaporX plugin, Player viewer, List<T> items) {
        super(plugin, viewer);
        this.items = items;
    }

    protected abstract ItemStack render(T item);

    protected abstract void onItemClick(T item, InventoryClickEvent event);

    /** Alt satırdaki ekstra düğmeler (46-48, 50-51). */
    protected void bottom() {
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void build() {
        int total = items.size();
        int pages = Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page >= pages) {
            page = pages - 1;
        }
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int index = start + i;
            if (index >= total) {
                break;
            }
            T item = items.get(index);
            set(i, render(item), e -> onItemClick(item, e));
        }
        if (total == 0) {
            set(22, button(Material.BARRIER, "menu.common.empty"));
        }
        if (page > 0) {
            set(45, button(Material.ARROW, "menu.common.prev"), e -> {
                page--;
                refresh();
            });
        }
        if (page < pages - 1) {
            set(53, button(Material.ARROW, "menu.common.next"), e -> {
                page++;
                refresh();
            });
        }
        set(52, button(Material.PAPER, "menu.common.page", "page", String.valueOf(page + 1), "pages", String.valueOf(pages)));
        addClose(49);
        bottom();
    }
}
