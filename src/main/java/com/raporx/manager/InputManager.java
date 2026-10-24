package com.raporx.manager;

import com.raporx.RaporX;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Menüden sohbete yazdırılan metinleri yakalar (sebep, isim, ayar değeri vb.). */
public final class InputManager implements Listener {
    private record Pending(Consumer<String> callback, long expires) {
    }

    private static final long TIMEOUT_MS = 60_000L;

    private final RaporX plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public InputManager(RaporX plugin) {
        this.plugin = plugin;
    }

    /** Envanteri kapatır, istemi gösterir ve oyuncunun bir sonraki mesajını callback'e verir. */
    public void request(Player player, String promptPath, Consumer<String> callback, String... kv) {
        player.closeInventory();
        pending.put(player.getUniqueId(), new Pending(callback, System.currentTimeMillis() + TIMEOUT_MS));
        plugin.messages().send(player, promptPath, kv);
        plugin.messages().send(player, "msg.input-cancel-hint");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Pending pd = pending.remove(event.getPlayer().getUniqueId());
        if (pd == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        String text = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (System.currentTimeMillis() > pd.expires()) {
                plugin.messages().send(player, "msg.input-expired");
                return;
            }
            if (text.equalsIgnoreCase("iptal") || text.equalsIgnoreCase("cancel")) {
                plugin.messages().send(player, "msg.input-cancelled");
                return;
            }
            pd.callback().accept(text);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
