package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.model.ReportDraft;
import com.raporx.model.TargetRef;
import com.raporx.util.ItemBuilder;
import com.raporx.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** /rapor: raporlanacak oyuncuyu seçtiren ilk menü. */
public final class PlayerSelectMenu extends PagedMenu<Player> {

    public PlayerSelectMenu(RaporX plugin, Player viewer) {
        super(plugin, viewer, candidates(viewer));
    }

    private static List<Player> candidates(Player viewer) {
        List<Player> list = new ArrayList<>(Bukkit.getOnlinePlayers());
        list.removeIf(p -> p.getUniqueId().equals(viewer.getUniqueId()));
        list.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        return list;
    }

    @Override
    protected String title() {
        return msgs().ui("menu.player-select.title");
    }

    @Override
    protected ItemStack render(Player p) {
        return new ItemBuilder(Material.PLAYER_HEAD)
                .head(p)
                .name(msgs().ui("menu.player-select.head.name", "player", p.getName()))
                .lore(msgs().uiList("menu.player-select.head.lore", "player", p.getName()))
                .build();
    }

    @Override
    protected void onItemClick(Player p, InventoryClickEvent event) {
        new CategoryMenu(plugin, viewer, new ReportDraft(new TargetRef(p.getUniqueId(), p.getName()))).open();
    }

    @Override
    protected void bottom() {
        set(47, button(Material.NAME_TAG, "menu.player-select.offline"),
                e -> plugin.input().request(viewer, "msg.input-offline-name", this::lookup));
        set(51, button(Material.BOOK, "menu.player-select.mine"), e -> MyReportsMenu.openFor(plugin, viewer));
    }

    private void lookup(String typed) {
        String name = Text.clean(typed, 32);
        plugin.directory().find(name).whenComplete((ref, ex) -> plugin.sync(() -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (ref == null) {
                plugin.messages().send(viewer, "msg.player-not-found", "player", name);
                return;
            }
            new CategoryMenu(plugin, viewer, new ReportDraft(ref)).open();
        }));
    }
}
