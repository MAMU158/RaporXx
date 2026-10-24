package com.raporx.listener;

import com.raporx.RaporX;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;

/** Oyuncuların mesaj ve komutlarını geçmişe yazar (iptal edilenler hariç). */
public final class ChatListener implements Listener {
    private final RaporX plugin;

    public ChatListener(RaporX plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        plugin.chatLog().log(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getMessage());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("logging.log-commands", true)) {
            return;
        }
        String message = event.getMessage();
        if (message.length() < 2) {
            return;
        }
        String label = message.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        for (String blocked : plugin.getConfig().getStringList("logging.command-blacklist")) {
            if (blocked.equalsIgnoreCase(label)) {
                return;
            }
        }
        plugin.chatLog().log(event.getPlayer().getUniqueId(), event.getPlayer().getName(), message);
    }
}
