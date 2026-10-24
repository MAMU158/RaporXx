package com.raporx.listener;

import com.raporx.RaporX;
import com.raporx.model.Report;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class JoinListener implements Listener {
    private final RaporX plugin;

    public JoinListener(RaporX plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.directory().touch(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            plugin.reports().deliverNotifications(player);
            if (player.hasPermission("rapor.notify") && plugin.getConfig().getBoolean("notify.join-summary", true)) {
                plugin.database().countByStatus(Report.Status.OPEN).thenAccept(count -> plugin.sync(() -> {
                    if (count != null && count > 0 && player.isOnline()) {
                        plugin.messages().send(player, "msg.join-summary", "count", String.valueOf(count));
                    }
                })).exceptionally(ex -> null);
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.directory().touch(event.getPlayer());
    }
}
