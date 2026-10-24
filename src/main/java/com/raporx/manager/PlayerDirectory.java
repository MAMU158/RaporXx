package com.raporx.manager;

import com.raporx.RaporX;
import com.raporx.model.TargetRef;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Çevrimdışı oyuncuları da bulabilmek için isim-UUID kayıtlarını tutar. */
public final class PlayerDirectory {
    private final RaporX plugin;

    public PlayerDirectory(RaporX plugin) {
        this.plugin = plugin;
    }

    public void touch(Player player) {
        plugin.database().upsertPlayer(player.getUniqueId(), player.getName(), System.currentTimeMillis())
                .exceptionally(ex -> null);
    }

    /** Ana thread'den çağır. Bulunamazsa sonuç null olur. */
    public CompletableFuture<TargetRef> find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(new TargetRef(online.getUniqueId(), online.getName()));
        }
        return plugin.database().findPlayer(name).exceptionally(ex -> null);
    }

    /** Tablo boşsa sunucunun daha önce tanıdığı oyuncuları içeri alır. */
    public void seedIfEmpty() {
        plugin.database().countPlayers().thenAccept(count -> {
            if (count == null || count != 0) {
                return;
            }
            plugin.sync(() -> {
                List<TargetRef> refs = new ArrayList<>();
                List<Long> seen = new ArrayList<>();
                for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                    if (op.getName() != null) {
                        refs.add(new TargetRef(op.getUniqueId(), op.getName()));
                        seen.add(op.getLastPlayed());
                    }
                }
                if (!refs.isEmpty()) {
                    plugin.database().seedPlayers(refs, seen).exceptionally(ex -> null);
                }
            });
        }).exceptionally(ex -> null);
    }
}
