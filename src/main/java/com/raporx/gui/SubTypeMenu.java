package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.model.ReportDraft;
import com.raporx.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/** Hile kategorisinde alt türü (KillAura, Fly...) seçtiren menü. */
public final class SubTypeMenu extends Menu {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final ReportDraft draft;

    public SubTypeMenu(RaporX plugin, Player viewer, ReportDraft draft) {
        super(plugin, viewer);
        this.draft = draft;
    }

    @Override
    protected String title() {
        return msgs().ui("menu.subtype.title", "player", draft.target.name());
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void build() {
        List<String> types = plugin.reports().subTypes(draft.category);
        for (int i = 0; i < types.size() && i < SLOTS.length; i++) {
            String type = types.get(i);
            set(SLOTS[i], new ItemBuilder(Material.REDSTONE_TORCH)
                            .name(msgs().msg("menu.subtype.item.name", "type", type))
                            .lore(msgs().uiList("menu.subtype.item.lore", "type", type))
                            .build(),
                    e -> {
                        draft.subType = type;
                        new ConfirmMenu(plugin, viewer, draft).open();
                    });
        }
        addBack(27, () -> new CategoryMenu(plugin, viewer, draft).open());
        addClose(31);
    }
}
