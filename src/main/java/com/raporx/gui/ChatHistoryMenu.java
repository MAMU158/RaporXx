package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.model.ChatEntry;
import com.raporx.util.ItemBuilder;
import com.raporx.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Oyuncunun son mesajları/komutları (en yeni başta). */
public final class ChatHistoryMenu extends PagedMenu<ChatEntry> {
    private final String targetName;
    private final Runnable back;

    /** entries eskiden yeniye sıralı gelir; menüde en yeni başta gösterilir. */
    public ChatHistoryMenu(RaporX plugin, Player viewer, List<ChatEntry> entries, String targetName, Runnable back) {
        super(plugin, viewer, newestFirst(entries));
        this.targetName = targetName;
        this.back = back;
    }

    private static List<ChatEntry> newestFirst(List<ChatEntry> entries) {
        List<ChatEntry> copy = new ArrayList<>(entries);
        Collections.reverse(copy);
        return copy;
    }

    @Override
    protected String title() {
        return msgs().ui("menu.chat.title", "player", targetName);
    }

    @Override
    protected ItemStack render(ChatEntry entry) {
        boolean command = entry.message().startsWith("/");
        return new ItemBuilder(command ? Material.COMMAND_BLOCK : Material.PAPER)
                .name(msgs().msg("menu.chat.item.name", "time", TimeUtil.clock(entry.time())))
                .lore(msgs().msg("menu.chat.item.lore", "message", entry.message()))
                .build();
    }

    @Override
    protected void onItemClick(ChatEntry item, InventoryClickEvent event) {
        // sadece okuma
    }

    @Override
    protected void bottom() {
        addBack(46, back);
        set(47, button(Material.WRITABLE_BOOK, "menu.chat.print"), e -> {
            viewer.closeInventory();
            plugin.messages().send(viewer, "msg.history-header", "player", targetName, "count", String.valueOf(items.size()));
            List<ChatEntry> oldestFirst = new ArrayList<>(items);
            Collections.reverse(oldestFirst);
            for (ChatEntry c : oldestFirst) {
                viewer.sendMessage(msgs().msg("msg.history-line", "time", TimeUtil.clock(c.time()), "message", c.message()));
            }
        });
    }
}
