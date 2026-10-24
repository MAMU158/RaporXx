package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.manager.ReportManager;
import com.raporx.model.Report;
import com.raporx.util.ItemBuilder;
import com.raporx.util.Text;
import com.raporx.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

/** Tek bir raporun detayı ve yetkili işlemleri. */
public final class ReportDetailMenu extends Menu {
    private final Report report;
    private final int totalAgainst;

    private ReportDetailMenu(RaporX plugin, Player viewer, Report report, int totalAgainst) {
        super(plugin, viewer);
        this.report = report;
        this.totalAgainst = totalAgainst;
    }

    public static void openFor(RaporX plugin, Player viewer, int id) {
        CompletableFuture<Report> reportF = plugin.database().getReport(id);
        reportF.thenCompose(r -> r == null
                        ? CompletableFuture.completedFuture(new Object[]{null, 0})
                        : plugin.database().countAgainst(r.targetUuid).thenApply(n -> new Object[]{r, n}))
                .whenComplete((pair, ex) -> plugin.sync(() -> {
                    if (!viewer.isOnline()) {
                        return;
                    }
                    if (ex != null || pair == null) {
                        plugin.messages().send(viewer, "msg.load-failed");
                        return;
                    }
                    Report r = (Report) pair[0];
                    if (r == null) {
                        plugin.messages().send(viewer, "msg.report-not-found", "id", String.valueOf(id));
                        return;
                    }
                    new ReportDetailMenu(plugin, viewer, r, (Integer) pair[1]).open();
                }));
    }

    @Override
    protected String title() {
        return msgs().ui("menu.detail.title", "id", String.valueOf(report.id));
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void build() {
        ReportManager rm = plugin.reports();
        Report r = report;
        ReportManager.Evidence ev = rm.evidence(r);
        String category = rm.categoryName(r.category) + (r.subType != null && !r.subType.isBlank() ? " › " + r.subType : "");
        String evidence = rm.isCheat(r.category) || ev.count() > 0 ? rm.evidenceSummary(ev, true) : "—";

        set(4, new ItemBuilder(Material.PLAYER_HEAD)
                .head(Bukkit.getOfflinePlayer(r.targetUuid))
                .glow(ev.level() == 2)
                .name(msgs().msg("menu.detail.summary.name", "id", String.valueOf(r.id), "target", r.targetName))
                .lore(msgs().uiList("menu.detail.summary.lore",
                        "target", r.targetName,
                        "reporter", r.reporterName,
                        "category", category,
                        "reason", dash(r.reason),
                        "time", TimeUtil.date(r.createdAt),
                        "status", rm.statusText(r.status),
                        "handler", dash(r.handledBy),
                        "note", dash(r.note),
                        "punishment", dash(r.punishment),
                        "total", String.valueOf(totalAgainst),
                        "info", dash(r.info),
                        "evidence", evidence))
                .build());

        set(10, button(Material.BOOK, "menu.detail.chat", "count", String.valueOf(r.chat.size())), e -> {
            if (e.isRightClick()) {
                int size = Math.max(1, plugin.getConfig().getInt("logging.history-size", 50));
                plugin.chatLog().recent(r.targetUuid, size).whenComplete((list, ex) -> plugin.sync(() -> {
                    if (ex != null || list == null) {
                        plugin.messages().send(viewer, "msg.load-failed");
                        return;
                    }
                    new ChatHistoryMenu(plugin, viewer, list, r.targetName, this::reopenSelf).open();
                }));
            } else {
                new ChatHistoryMenu(plugin, viewer, r.chat, r.targetName, this::reopenSelf).open();
            }
        });

        set(12, button(Material.REDSTONE, "menu.detail.flags", "count", String.valueOf(r.flags.size())), e -> {
            if (e.isRightClick()) {
                plugin.database().recentFlags(r.targetUuid, 0L, 100).whenComplete((list, ex) -> plugin.sync(() -> {
                    if (ex != null || list == null) {
                        plugin.messages().send(viewer, "msg.load-failed");
                        return;
                    }
                    new FlagsMenu(plugin, viewer, list, r.targetName, this::reopenSelf).open();
                }));
            } else {
                new FlagsMenu(plugin, viewer, r.flags, r.targetName, this::reopenSelf).open();
            }
        });

        Player online = Bukkit.getPlayer(r.targetUuid);
        if (viewer.hasPermission("rapor.teleport") && online != null) {
            set(14, button(Material.ENDER_PEARL, "menu.detail.teleport"), e -> {
                viewer.closeInventory();
                viewer.teleport(online.getLocation());
                plugin.messages().send(viewer, "msg.teleported", "player", online.getName());
            });
        } else {
            set(14, button(Material.GRAY_DYE, "menu.detail.teleport-off"));
        }

        if (viewer.hasPermission("rapor.punish")) {
            set(16, button(Material.ANVIL, "menu.detail.punish"), e -> new PunishMenu(plugin, viewer, r, this::reopenSelf).open());
        }

        set(29, button(Material.LIME_DYE, "menu.detail.resolve"), e -> {
            if (e.isRightClick()) {
                askNote(Report.Status.RESOLVED);
            } else {
                plugin.reports().setStatus(viewer, r, Report.Status.RESOLVED, null, null);
                reopenSelf();
            }
        });
        set(31, button(Material.RED_DYE, "menu.detail.reject"), e -> {
            if (e.isRightClick()) {
                askNote(Report.Status.REJECTED);
            } else {
                plugin.reports().setStatus(viewer, r, Report.Status.REJECTED, null, null);
                reopenSelf();
            }
        });
        set(33, button(Material.CLOCK, "menu.detail.reopen"), e -> {
            plugin.reports().reopen(viewer, r);
            reopenSelf();
        });

        addBack(36, () -> AdminPanelMenu.openFor(plugin, viewer, Report.Status.OPEN));
        addClose(40);
    }

    private void askNote(Report.Status status) {
        plugin.input().request(viewer, "msg.input-note", text -> {
            plugin.reports().setStatus(viewer, report, status, Text.clean(text, 150), null);
            if (viewer.isOnline()) {
                reopenSelf();
            }
        });
    }

    /** Güncel halini veritabanından yükleyip menüyü yeniden açar. */
    private void reopenSelf() {
        openFor(plugin, viewer, report.id);
    }
}
