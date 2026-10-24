package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.model.Report;
import com.raporx.util.ItemBuilder;
import com.raporx.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Oyuncunun kendi gönderdiği raporlar ve sonuçları. */
public final class MyReportsMenu extends PagedMenu<Report> {

    private MyReportsMenu(RaporX plugin, Player viewer, List<Report> reports) {
        super(plugin, viewer, reports);
    }

    public static void openFor(RaporX plugin, Player viewer) {
        plugin.database().listReportsByReporter(viewer.getUniqueId(), 90).whenComplete((list, ex) -> plugin.sync(() -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (ex != null || list == null) {
                plugin.messages().send(viewer, "msg.load-failed");
                return;
            }
            new MyReportsMenu(plugin, viewer, list).open();
        }));
    }

    @Override
    protected String title() {
        return msgs().ui("menu.my-reports.title");
    }

    @Override
    protected ItemStack render(Report r) {
        Material material = switch (r.status) {
            case OPEN -> Material.PAPER;
            case RESOLVED -> Material.LIME_DYE;
            case REJECTED -> Material.RED_DYE;
        };
        String category = plugin.reports().categoryName(r.category)
                + (r.subType != null && !r.subType.isBlank() ? " › " + r.subType : "");
        return new ItemBuilder(material)
                .name(msgs().msg("menu.my-reports.item.name", "id", String.valueOf(r.id), "target", r.targetName))
                .lore(msgs().uiList("menu.my-reports.item.lore",
                        "category", category,
                        "status", plugin.reports().statusText(r.status),
                        "time", TimeUtil.ago(r.createdAt),
                        "punishment", dash(r.punishment)))
                .build();
    }

    @Override
    protected void onItemClick(Report item, InventoryClickEvent event) {
        // sadece bilgi amaçlı
    }

    @Override
    protected void bottom() {
        addBack(46, () -> new PlayerSelectMenu(plugin, viewer).open());
    }
}
