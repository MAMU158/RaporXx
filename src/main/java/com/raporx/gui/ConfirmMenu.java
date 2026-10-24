package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.model.ReportDraft;
import com.raporx.util.ItemBuilder;
import com.raporx.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Raporu özetler; sebep yazdırır ve gönderimi onaylatır. */
public final class ConfirmMenu extends Menu {
    private final ReportDraft draft;

    public ConfirmMenu(RaporX plugin, Player viewer, ReportDraft draft) {
        super(plugin, viewer);
        this.draft = draft;
    }

    @Override
    protected String title() {
        return msgs().ui("menu.confirm.title");
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void build() {
        String category = draft.category == null ? "—" : plugin.reports().categoryName(draft.category);
        set(4, new ItemBuilder(Material.PLAYER_HEAD)
                .head(Bukkit.getOfflinePlayer(draft.target.uuid()))
                .name(msgs().ui("menu.confirm.summary.name", "player", draft.target.name()))
                .lore(msgs().uiList("menu.confirm.summary.lore",
                        "player", draft.target.name(),
                        "category", category,
                        "type", dash(draft.subType),
                        "reason", dash(draft.reason)))
                .build());

        set(20, button(Material.WRITABLE_BOOK, "menu.confirm.reason"),
                e -> plugin.input().request(viewer, "msg.input-reason", this::setReason));
        set(22, button(Material.LIME_CONCRETE, "menu.confirm.accept"), e -> {
            viewer.closeInventory();
            plugin.reports().submit(viewer, draft);
        });
        set(24, button(Material.RED_CONCRETE, "menu.confirm.cancel"), e -> viewer.closeInventory());

        addBack(27, () -> {
            if (draft.subType != null) {
                new SubTypeMenu(plugin, viewer, draft).open();
            } else {
                new CategoryMenu(plugin, viewer, draft).open();
            }
        });
        addClose(31);
    }

    private void setReason(String typed) {
        int max = plugin.getConfig().getInt("report.max-reason-length", 200);
        draft.reason = Text.clean(typed, max);
        if (viewer.isOnline()) {
            new ConfirmMenu(plugin, viewer, draft).open();
        }
    }
}
