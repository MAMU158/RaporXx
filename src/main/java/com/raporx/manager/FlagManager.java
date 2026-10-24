package com.raporx.manager;

import com.raporx.RaporX;
import com.raporx.model.FlagEntry;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hile korumasından gelen flag'leri kaydeder. Raporlar bu kayıtlara dayanır;
 * ayarlıysa flag'ler eşiği geçince otomatik rapor da açılır.
 */
public final class FlagManager {
    private final RaporX plugin;
    private final Map<UUID, Long> autoCooldown = new ConcurrentHashMap<>();

    public FlagManager(RaporX plugin) {
        this.plugin = plugin;
    }

    public void add(UUID uuid, String name, String check, double vl, String details) {
        String safeCheck = check == null || check.isBlank() ? "Bilinmeyen" : check;
        String safeDetails = details == null ? "" : details;
        plugin.database().insertFlag(uuid, name, safeCheck, vl, safeDetails, System.currentTimeMillis())
                .thenRun(() -> plugin.sync(() -> checkAutoReport(uuid, name)))
                .exceptionally(ex -> null);
    }

    private void checkAutoReport(UUID uuid, String name) {
        ConfigurationSection ac = plugin.getConfig().getConfigurationSection("anticheat.auto-report");
        if (ac == null || !ac.getBoolean("enabled", false)) {
            return;
        }
        long now = System.currentTimeMillis();
        long since = now - ac.getLong("window-minutes", 5) * 60_000L;
        double threshold = ac.getDouble("vl-threshold", 30);
        long cooldown = ac.getLong("cooldown-minutes", 15) * 60_000L;
        String category = ac.getString("category", "hile");

        plugin.database().recentFlags(uuid, since, 300).thenAccept(list -> plugin.sync(() -> {
            double sum = 0;
            Map<String, Double> perCheck = new HashMap<>();
            for (FlagEntry f : list) {
                sum += f.vl();
                perCheck.merge(f.check(), f.vl(), Double::sum);
            }
            if (sum < threshold) {
                return;
            }
            Long last = autoCooldown.get(uuid);
            if (last != null && now - last < cooldown) {
                return;
            }
            autoCooldown.put(uuid, now);
            String top = perCheck.entrySet().stream().max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey).orElse(null);
            String reason = plugin.messages().msg("msg.auto-reason",
                    "check", top == null ? "-" : top, "vl", String.format("%.1f", sum),
                    "minutes", String.valueOf(ac.getLong("window-minutes", 5)));
            plugin.reports().createAuto(uuid, name, category, top, com.raporx.util.Text.strip(reason));
        })).exceptionally(ex -> null);
    }
}
