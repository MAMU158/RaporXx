package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.manager.ReportManager;
import com.raporx.model.ReportDraft;
import com.raporx.util.ItemBuilder;
import com.raporx.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Rapor kategorisini seçtiren menü. */
public final class CategoryMenu extends Menu {
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private final ReportDraft draft;

    public CategoryMenu(RaporX plugin, Player viewer, ReportDraft draft) {
        super(plugin, viewer);
        this.draft = draft;
    }

    @Override
    protected String title() {
        return msgs().ui("menu.category.title", "player", draft.target.name());
    }

    @Override
    protected int rows() {
        return 4;
    }

    @Override
    protected void build() {
        ReportManager rm = plugin.reports();
        List<String> ids = rm.categoryIds(true);
        for (int i = 0; i < ids.size() && i < SLOTS.length; i++) {
            String id = ids.get(i);
            ConfigurationSection s = rm.category(id);
            if (s == null) {
                continue;
            }
            List<String> lore = new ArrayList<>();
            for (String line : s.getStringList("lore")) {
                lore.add(Text.color(line));
            }
            lore.add(" ");
            lore.add(msgs().ui(rm.isCheat(id) && !rm.subTypes(id).isEmpty() ? "menu.category.click-types" : "menu.category.click"));
            ItemStack item = ItemBuilder.of(s.getString("icon"), Material.PAPER)
                    .name(rm.categoryName(id))
                    .lore(lore)
                    .glow(rm.isCheat(id))
                    .build();
            set(SLOTS[i], item, e -> {
                draft.category = id;
                draft.subType = null;
                if (rm.isCheat(id) && !rm.subTypes(id).isEmpty()) {
                    new SubTypeMenu(plugin, viewer, draft).open();
                } else {
                    new ConfirmMenu(plugin, viewer, draft).open();
                }
            });
        }
        addBack(27, () -> new PlayerSelectMenu(plugin, viewer).open());
        addClose(31);
    }
}