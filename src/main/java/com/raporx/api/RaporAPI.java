package com.raporx.api;

import com.raporx.RaporX;

import java.util.UUID;

/**
 * Diğer pluginlerin (örn. kendi anti-cheat'in) kullanabileceği küçük API.
 *
 * <pre>
 * RaporAPI.addFlag(player.getUniqueId(), player.getName(), "KillAura", 4.5, "reach=3.9");
 * </pre>
 *
 * Konsol komutu ile aynısı: /raporadmin flag &lt;oyuncu&gt; &lt;kontrol&gt; &lt;vl&gt; [detay]
 */
public final class RaporAPI {
    private RaporAPI() {
    }

    /** Hile korumasından bir flag kaydeder. Herhangi bir thread'den çağrılabilir. */
    public static void addFlag(UUID uuid, String playerName, String check, double vl, String details) {
        RaporX.getInstance().flags().add(uuid, playerName, check, vl, details);
    }

    /** Sistem adına (otomatik) rapor açar. Ana thread'den çağır. */
    public static void createAutoReport(UUID uuid, String playerName, String category, String subType, String reason) {
        RaporX.getInstance().reports().createAuto(uuid, playerName, category, subType, reason);
    }
}
