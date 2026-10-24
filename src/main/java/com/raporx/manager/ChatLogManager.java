package com.raporx.manager;

import com.raporx.RaporX;
import com.raporx.model.ChatEntry;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Oyuncuların son mesajlarını (ve istenirse komutlarını) veritabanında tutar. */
public final class ChatLogManager {
    private static final int MAX_LENGTH = 256;

    private final RaporX plugin;

    public ChatLogManager(RaporX plugin) {
        this.plugin = plugin;
    }

    public void log(UUID uuid, String name, String message) {
        String msg = message.length() > MAX_LENGTH ? message.substring(0, MAX_LENGTH) : message;
        plugin.database().insertChat(uuid, name, System.currentTimeMillis(), msg).exceptionally(ex -> null);
    }

    public CompletableFuture<List<ChatEntry>> recent(UUID uuid, int count) {
        return plugin.database().recentChat(uuid, count);
    }

    /** Oyuncu başına history-size kadar mesaj bırakır, eski flag'leri siler. */
    public void prune() {
        int keep = Math.max(1, plugin.getConfig().getInt("logging.history-size", 50));
        plugin.database().pruneChat(keep).exceptionally(ex -> null);
        long days = plugin.getConfig().getLong("anticheat.retention-days", 30);
        plugin.database().pruneFlags(System.currentTimeMillis() - days * 86_400_000L).exceptionally(ex -> null);
    }
}
