package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.model.FlagEntry;
import com.raporx.util.ItemBuilder;
import com.raporx.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Hile korumasından gelen flag kayıtları (en yeni başta). */
public final class FlagsMenu extends PagedMenu<FlagEntry> {
    private final String targetName;
    private final Runnable back;

    public FlagsMenu(RaporX plugin, Player viewer, List<FlagEntry> flags, String targetName, Runnable back) {
        super(plugin, viewer, flags);
        this.targetName = targetName;
        this.back = back;
    }

    @Override
    protected String title() {
        return msgs().ui("menu.flags.title", "player", targetName);
    }

    @Override
    protected ItemStack render(FlagEntry f) {
        return new ItemBuilder(f.vl() >= 10 ? Material.REDSTONE_BLOCK : Material.REDSTONE_TORCH)
                .name(msgs().msg("menu.flags.item.name", "check", f.check()))
                .lore(msgs().uiList("menu.flags.item.lore",
                        "vl", String.format("%.1f", f.vl()),
                        "time", TimeUtil.ago(f.time()),
                        "clock", TimeUtil.clock(f.time()),
                        "details", dash(f.details())))
                .build();
    }

    @Override
    protected void onItemClick(FlagEntry item, InventoryClickEvent event) {
        // sadece okuma
    }

    @Override
    protected void bottom() {
        addBack(46, back);
    }
}
