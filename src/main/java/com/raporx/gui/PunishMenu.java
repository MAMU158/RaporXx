package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.model.Report;
import com.raporx.util.ItemBuilder;
import com.raporx.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Rapor edilen oyuncuya (çevrimdışı olsa da) ceza verme menüsü. */
public final class PunishMenu extends Menu {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final Report report;
    private final Runnable back;
    private String selected;
    private String reason;

    public PunishMenu(RaporX plugin, Player viewer, Report report, Runnable back) {
        super(plugin, viewer);
        this.report = report;
        this.back = back;
        this.reason = plugin.reports().defaultPunishReason(report);
    }

    @Override
    protected String title() {
        return msgs().ui("menu.punish.title", "player", report.targetName);
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void build() {
        List<String> ids = plugin.reports().punishmentIds();
        for (int i = 0; i < ids.size() && i < SLOTS.length; i++) {
            String id = ids.get(i);
            ConfigurationSection s = plugin.getConfig().getConfigurationSection("punishments.list." + id);
            if (s == null) {
                continue;
            }
            List<String> lore = new ArrayList<>();
            for (String line : s.getStringList("lore")) {
                lore.add(Text.color(line));
            }
            lore.add(" ");
            lore.add(msgs().ui(id.equals(selected) ? "menu.punish.selected" : "menu.punish.select"));
            set(SLOTS[i], ItemBuilder.of(s.getString("icon"), Material.PAPER)
                    .name(plugin.reports().punishmentName(id))
                    .lore(lore)
                    .glow(id.equals(selected))
                    .build(), e -> {
                selected = id;
                refresh();
            });
        }

        set(29, button(Material.WRITABLE_BOOK, "menu.punish.reason", "reason", reason),
                e -> plugin.input().request(viewer, "msg.input-punish-reason", text -> {
                    String cleaned = Text.clean(text, 100);
                    if (!cleaned.isEmpty()) {
                        reason = cleaned;
                    }
                    if (viewer.isOnline()) {
                        open();
                    }
                }));

        if (selected != null) {
            set(31, button(Material.LIME_CONCRETE, "menu.punish.apply",
                    "punishment", Text.strip(plugin.reports().punishmentName(selected)),
                    "player", report.targetName,
                    "reason", reason), e -> {
                if (plugin.reports().punish(viewer, report, selected, reason)) {
                    viewer.closeInventory();
                    back.run();
                }
            });
        } else {
            set(31, button(Material.GRAY_CONCRETE, "menu.punish.apply-off"));
        }

        addBack(36, back);
        addClose(40);
    }
}
