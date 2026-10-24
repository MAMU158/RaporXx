package com.raporx.manager;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.raporx.RaporX;
import com.raporx.model.ChatEntry;
import com.raporx.model.FlagEntry;
import com.raporx.model.Report;
import com.raporx.util.Text;
import com.raporx.util.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/** Discord webhook gönderimi: embed + (istenirse) son 50 mesajın .txt dosyası. */
public final class WebhookManager {
    private static final Pattern URL_PATTERN =
            Pattern.compile("^https://(?:(?:canary|ptb)\\.)?discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+$");
    private static final Gson GSON = new Gson();

    private final RaporX plugin;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RaporX-Webhook");
        t.setDaemon(true);
        return t;
    });

    public WebhookManager(RaporX plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        exec.shutdown();
    }

    public static boolean isValidUrl(String url) {
        return url != null && URL_PATTERN.matcher(url.trim()).matches();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("discord.enabled", false)
                && isValidUrl(plugin.getConfig().getString("discord.webhook-url", ""));
    }

    // -------------------------------------------------------------- rapor

    public void sendReport(Report r) {
        if (!enabled()) {
            return;
        }
        ReportManager rm = plugin.reports();
        ConfigurationSection d = plugin.getConfig().getConfigurationSection("discord");
        if (d == null) {
            return;
        }
        boolean cheat = rm.isCheat(r.category);
        ReportManager.Evidence ev = rm.evidence(r);

        String url = d.getString("webhook-url", "");
        String cheatUrl = d.getString("cheat-webhook-url", "");
        if (cheat && isValidUrl(cheatUrl)) {
            url = cheatUrl;
        }

        String role = d.getString("mention-role-id", "").trim();
        boolean mention = role.matches("\\d{5,25}") && (!d.getBoolean("mention-only-cheat", true) || cheat);
        JsonObject payload = basePayload(d, mention ? role : null);
        if (mention) {
            payload.addProperty("content", "<@&" + role + ">");
        }

        JsonObject embed = new JsonObject();
        embed.addProperty("title", d.getString("title", "Yeni Rapor #{id}").replace("{id}", String.valueOf(r.id)));
        embed.addProperty("color", color(d, cheat ? (ev.level() == 2 ? "cheat-high" : ev.level() == 1 ? "cheat-low" : "cheat-none") : "default"));
        embed.addProperty("timestamp", Instant.ofEpochMilli(r.createdAt).toString());
        if (d.getBoolean("show-head", true) && r.targetName.matches("[A-Za-z0-9_]{1,16}")) {
            JsonObject thumb = new JsonObject();
            thumb.addProperty("url", "https://minotar.net/helm/" + r.targetName + "/64.png");
            embed.add("thumbnail", thumb);
        }
        JsonObject footer = new JsonObject();
        footer.addProperty("text", plugin.getConfig().getString("server-name", "Sunucu") + " • RaporX");
        embed.add("footer", footer);

        JsonArray fields = new JsonArray();
        field(fields, label("target"), "**" + r.targetName + "**\n`" + r.targetUuid + "`", true);
        field(fields, label("reporter"), r.auto ? r.reporterName : "**" + r.reporterName + "**", true);
        String cat = rm.categoryPlain(r.category) + (r.subType != null && !r.subType.isBlank() ? " › " + r.subType : "");
        field(fields, label("category"), cat, false);
        field(fields, label("reason"), r.reason, false);
        if (cheat || ev.count() > 0) {
            field(fields, label("evidence"), evidenceText(r, ev), false);
        }
        field(fields, label("state"), r.info, false);

        int lines = Math.max(0, d.getInt("embed-chat-lines", 8));
        if (lines > 0) {
            String block = chatBlock(r.chat, lines);
            field(fields, label("chat", "count", String.valueOf(r.chat.size())),
                    block == null ? label("no-chat") : block, false);
        }
        embed.add("fields", fields);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        boolean file = d.getBoolean("send-chat-file", true) && (!r.chat.isEmpty() || !r.flags.isEmpty());
        post(url, payload, file ? "rapor-" + r.id + ".txt" : null, file ? transcript(r) : null);
    }

    /** Ceza / çözüldü / reddedildi / yeniden açıldı bildirimi. */
    public void sendAction(Report r, String adminName) {
        if (!enabled() || !plugin.getConfig().getBoolean("discord.notify-actions", true)) {
            return;
        }
        ConfigurationSection d = plugin.getConfig().getConfigurationSection("discord");
        if (d == null) {
            return;
        }
        ReportManager rm = plugin.reports();
        JsonObject payload = basePayload(d, null);
        JsonObject embed = new JsonObject();
        embed.addProperty("title", label("action-title", "id", String.valueOf(r.id)));
        embed.addProperty("color", color(d, r.status == Report.Status.RESOLVED ? "resolved"
                : r.status == Report.Status.REJECTED ? "rejected" : "default"));
        embed.addProperty("timestamp", Instant.now().toString());
        JsonObject footer = new JsonObject();
        footer.addProperty("text", plugin.getConfig().getString("server-name", "Sunucu") + " • RaporX");
        embed.add("footer", footer);

        JsonArray fields = new JsonArray();
        field(fields, label("target"), "**" + r.targetName + "**", true);
        field(fields, label("handler"), adminName, true);
        field(fields, label("status"), rm.statusText(r.status), true);
        field(fields, label("category"), rm.categoryPlain(r.category), true);
        if (r.punishment != null && !r.punishment.isBlank()) {
            field(fields, label("punishment"), r.punishment, true);
        }
        if (r.note != null && !r.note.isBlank()) {
            field(fields, label("note"), r.note, false);
        }
        embed.add("fields", fields);
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        String url = d.getString("webhook-url", "");
        String cheatUrl = d.getString("cheat-webhook-url", "");
        if (rm.isCheat(r.category) && isValidUrl(cheatUrl)) {
            url = cheatUrl;
        }
        post(url, payload, null, null);
    }

    /** Ayarlar menüsündeki test butonu. Sonuç: HTTP kodu, hata olursa -1. */
    public CompletableFuture<Integer> sendTest() {
        ConfigurationSection d = plugin.getConfig().getConfigurationSection("discord");
        String url = d == null ? "" : d.getString("webhook-url", "");
        if (d == null || !isValidUrl(url)) {
            return CompletableFuture.completedFuture(-1);
        }
        JsonObject payload = basePayload(d, null);
        JsonObject embed = new JsonObject();
        embed.addProperty("title", label("test-title"));
        embed.addProperty("description", label("test-description"));
        embed.addProperty("color", color(d, "resolved"));
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        return CompletableFuture.supplyAsync(() -> send(url, payload, null, null), exec);
    }

    // ------------------------------------------------------------ yardımcı

    private JsonObject basePayload(ConfigurationSection d, String roleToMention) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", d.getString("username", "RaporX"));
        String avatar = d.getString("avatar-url", "");
        if (avatar != null && avatar.startsWith("https://")) {
            payload.addProperty("avatar_url", avatar);
        }
        // Oyuncu mesajlarındaki @everyone / @here asla etiketlemesin
        JsonObject allowed = new JsonObject();
        allowed.add("parse", new JsonArray());
        if (roleToMention != null) {
            JsonArray roles = new JsonArray();
            roles.add(roleToMention);
            allowed.add("roles", roles);
        }
        payload.add("allowed_mentions", allowed);
        return payload;
    }

    private String label(String key, String... kv) {
        return Text.strip(plugin.messages().msg("discord." + key, kv));
    }

    private static int color(ConfigurationSection d, String key) {
        String hex = d.getString("colors." + key, "#FFD166").replace("#", "").trim();
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0xFFD166;
        }
    }

    private static void field(JsonArray arr, String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", cut(name, 256));
        f.addProperty("value", value == null || value.isBlank() ? "—" : cut(value, 1024));
        f.addProperty("inline", inline);
        arr.add(f);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private String evidenceText(Report r, ReportManager.Evidence ev) {
        StringBuilder sb = new StringBuilder(plugin.reports().evidenceSummary(ev, false));
        int shown = 0;
        for (FlagEntry f : r.flags) {
            if (shown++ >= 5) {
                break;
            }
            sb.append("\n• `").append(f.check()).append("` VL ").append(String.format("%.1f", f.vl()))
                    .append(" — ").append(TimeUtil.ago(f.time()));
        }
        return sb.toString();
    }

    /** Embed içinde gösterilecek, ``` bloğu içindeki son mesajlar. */
    private static String chatBlock(List<ChatEntry> chat, int maxLines) {
        if (chat.isEmpty()) {
            return null;
        }
        Deque<String> lines = new ArrayDeque<>();
        int length = 8;
        int taken = 0;
        for (int i = chat.size() - 1; i >= 0 && taken < maxLines; i--) {
            ChatEntry c = chat.get(i);
            String line = "[" + TimeUtil.clock(c.time()) + "] " + c.message().replace("```", "'''");
            if (line.length() > 150) {
                line = line.substring(0, 149) + "…";
            }
            if (length + line.length() + 1 > 950) {
                break;
            }
            lines.addFirst(line);
            length += line.length() + 1;
            taken++;
        }
        if (lines.isEmpty()) {
            return null;
        }
        return "```\n" + String.join("\n", lines) + "\n```";
    }

    /** .txt eki: tam mesaj geçmişi + anti-cheat kayıtları. */
    public String transcript(Report r) {
        ReportManager rm = plugin.reports();
        StringBuilder sb = new StringBuilder();
        sb.append("RAPOR #").append(r.id).append('\n');
        sb.append("Raporlanan : ").append(r.targetName).append(" (").append(r.targetUuid).append(")\n");
        sb.append("Raporlayan : ").append(r.reporterName).append('\n');
        sb.append("Kategori   : ").append(rm.categoryPlain(r.category));
        if (r.subType != null && !r.subType.isBlank()) {
            sb.append(" > ").append(r.subType);
        }
        sb.append('\n');
        sb.append("Sebep      : ").append(r.reason == null ? "-" : r.reason).append('\n');
        sb.append("Tarih      : ").append(TimeUtil.full(r.createdAt)).append('\n');
        sb.append("Durum      : ").append(r.info == null ? "-" : r.info).append('\n');
        sb.append("\n=== Son ").append(r.chat.size()).append(" mesaj/komut ===\n");
        if (r.chat.isEmpty()) {
            sb.append("(kayıt yok)\n");
        }
        for (ChatEntry c : r.chat) {
            sb.append('[').append(TimeUtil.full(c.time())).append("] ").append(c.message()).append('\n');
        }
        sb.append("\n=== Anti-Cheat kayıtları (").append(r.flags.size()).append(") ===\n");
        if (r.flags.isEmpty()) {
            sb.append("(kayıt yok)\n");
        }
        for (FlagEntry f : r.flags) {
            sb.append('[').append(TimeUtil.full(f.time())).append("] ").append(f.check())
                    .append("  VL=").append(String.format("%.2f", f.vl()));
            if (f.details() != null && !f.details().isBlank()) {
                sb.append("  ").append(f.details());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------- HTTP

    private void post(String url, JsonObject payload, String fileName, String fileContent) {
        if (exec.isShutdown()) {
            return;
        }
        exec.execute(() -> send(url, payload, fileName, fileContent));
    }

    /** Webhook thread'inde çalışır. 429 gelirse bekleyip tekrar dener. Sonuç HTTP kodu, hata olursa -1. */
    private int send(String url, JsonObject payload, String fileName, String fileContent) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url.trim()))
                        .timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "RaporX/1.0");
                HttpRequest request;
                if (fileName != null && fileContent != null) {
                    String boundary = "----RaporX" + UUID.randomUUID().toString().replace("-", "");
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    write(out, "--" + boundary + "\r\n");
                    write(out, "Content-Disposition: form-data; name=\"payload_json\"\r\n"
                            + "Content-Type: application/json\r\n\r\n");
                    write(out, GSON.toJson(payload));
                    write(out, "\r\n--" + boundary + "\r\n");
                    write(out, "Content-Disposition: form-data; name=\"files[0]\"; filename=\"" + fileName + "\"\r\n"
                            + "Content-Type: text/plain; charset=utf-8\r\n\r\n");
                    write(out, fileContent);
                    write(out, "\r\n--" + boundary + "--\r\n");
                    request = b.header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
                } else {
                    request = b.header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8)).build();
                }
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                int code = response.statusCode();
                if (code == 429) {
                    Thread.sleep(Math.min(retryAfterMillis(response.body()), 10_000L));
                    continue;
                }
                if (code < 200 || code >= 300) {
                    String body = response.body() == null ? "" : response.body();
                    plugin.getLogger().warning("Discord webhook HTTP " + code + ": "
                            + (body.length() > 200 ? body.substring(0, 200) : body));
                }
                return code;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            } catch (Exception e) {
                plugin.getLogger().warning("Discord webhook gönderilemedi: " + e.getMessage());
                return -1;
            }
        }
        return 429;
    }

    private static void write(ByteArrayOutputStream out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
    }

    private static long retryAfterMillis(String body) {
        try {
            double seconds = JsonParser.parseString(body).getAsJsonObject().get("retry_after").getAsDouble();
            return (long) (seconds * 1000) + 100;
        } catch (RuntimeException e) {
            return 1500;
        }
    }
}
