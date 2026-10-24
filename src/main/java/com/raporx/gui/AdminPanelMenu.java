package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.manager.ReportManager;
import com.raporx.model.Report;
import com.raporx.util.ItemBuilder;
import com.raporx.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** /raporadmin: raporları listeleyen yönetim paneli. */
public final class AdminPanelMenu extends PagedMenu<Report> {
    private static final Report.Status[] FILTERS = {Report.Status.OPEN, Report.Status.RESOLVED, Report.Status.REJECTED, null};

    private final Report.Status filter;

    private AdminPanelMenu(RaporX plugin, Player viewer, List<Report> reports, Report.Status filter) {
        super(plugin, viewer, reports);
        this.filter = filter;
    }

    public static void openFor(RaporX plugin, Player viewer, Report.Status filter) {
        plugin.database().listReports(filter, 200).whenComplete((list, ex) -> plugin.sync(() -> {
            if (!viewer.isOnline()) {
                return;
            }
            if (ex != null || list == null) {
                plugin.messages().send(viewer, "msg.load-failed");
                return;
            }
            new AdminPanelMenu(plugin, viewer, list, filter).open();
        }));
    }

    @Override
    protected String title() {
        return msgs().ui("menu.admin.title", "filter", plugin.reports().statusText(filter));
    }

    @Override
    protected ItemStack render(Report r) {
        ReportManager rm = plugin.reports();
        ReportManager.Evidence ev = rm.evidence(r);
        String category = rm.categoryName(r.category) + (r.subType != null && !r.subType.isBlank() ? " › " + r.subType : "");
        String evidence = rm.isCheat(r.category) || ev.count() > 0 ? rm.evidenceSummary(ev, true) : "—";
        return new ItemBuilder(Material.PLAYER_HEAD)
                .head(Bukkit.getOfflinePlayer(r.targetUuid))
                .glow(r.status == Report.Status.OPEN && ev.level() == 2)
                .name(msgs().msg("menu.admin.item.name", "id", String.valueOf(r.id), "target", r.targetName))
                .lore(msgs().uiList("menu.admin.item.lore",
                        "reporter", r.reporterName,
                        "category", category,
                        "reason", dash(r.reason),
                        "time", TimeUtil.ago(r.createdAt),
                        "status", rm.statusText(r.status),
                        "evidence", evidence))
                .build();
    }

    @Override
    protected void onItemClick(Report r, InventoryClickEvent event) {
        ReportDetailMenu.openFor(plugin, viewer, r.id);
    }

    @Override
    protected void bottom() {
        set(47, button(Material.HOPPER, "menu.admin.filter", "filter", plugin.reports().statusText(filter)), e -> {
            int index = 0;
            for (int i = 0; i < FILTERS.length; i++) {
                if (FILTERS[i] == filter) {
                    index = i;
                }
            }
            openFor(plugin, viewer, FILTERS[(index + 1) % FILTERS.length]);
        });
        set(48, button(Material.CLOCK, "menu.admin.refresh"), e -> openFor(plugin, viewer, filter));
        if (viewer.hasPermission("rapor.settings")) {
            set(51, button(Material.COMPARATOR, "menu.admin.settings"), e -> new SettingsMenu(plugin, viewer).open());
        }
    }
}
