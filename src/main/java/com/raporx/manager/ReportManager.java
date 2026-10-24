package com.raporx.manager;

import com.raporx.Messages;
import com.raporx.RaporX;
import com.raporx.model.FlagEntry;
import com.raporx.model.Report;
import com.raporx.model.ReportDraft;
import com.raporx.util.Text;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/** Rapor oluşturma, doğrulama, durum değiştirme, ceza uygulama ve bildirimler. */
public final class ReportManager {
    /** Doğrulama hatası: mesaj yolu + yer tutucular. */
    public record Err(String path, String[] kv) {
    }

    /** Anti-cheat kanıt özeti. level: 0 yok, 1 zayıf, 2 güçlü. */
    public record Evidence(int count, double sumVl, double maxVl, String topCheck, int level) {
    }

    private static final UUID AUTO_UUID = new UUID(0L, 0L);
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.]{1,24}");

    private final RaporX plugin;
    private final Map<UUID, Long> lastReport = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> hourly = new ConcurrentHashMap<>();
    private final Map<String, Long> pairs = new ConcurrentHashMap<>();

    public ReportManager(RaporX plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private Messages messages() {
        return plugin.messages();
    }

    // ------------------------------------------------------------ kategori

    public List<String> categoryIds(boolean onlyEnabled) {
        List<String> ids = new ArrayList<>();
        ConfigurationSection root = cfg().getConfigurationSection("categories");
        if (root == null) {
            return ids;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s != null && (!onlyEnabled || s.getBoolean("enabled", true))) {
                ids.add(id);
            }
        }
        return ids;
    }

    public ConfigurationSection category(String id) {
        return id == null ? null : cfg().getConfigurationSection("categories." + id);
    }

    /** Renkli kategori adı. */
    public String categoryName(String id) {
        ConfigurationSection s = category(id);
        return Text.color(s == null ? String.valueOf(id) : s.getString("name", id));
    }

    public String categoryPlain(String id) {
        return Text.strip(categoryName(id));
    }

    public boolean isCheat(String id) {
        ConfigurationSection s = category(id);
        return s != null && s.getBoolean("cheat", false);
    }

    public boolean requireReason(String id) {
        ConfigurationSection s = category(id);
        return s != null && s.getBoolean("require-reason", false);
    }

    public List<String> subTypes(String id) {
        ConfigurationSection s = category(id);
        return s == null ? new ArrayList<>() : s.getStringList("types");
    }

    public String statusText(Report.Status status) {
        String key = status == null ? "all" : status.name().toLowerCase(Locale.ROOT);
        return Text.strip(messages().msg("status." + key));
    }

    public List<String> punishmentIds() {
        ConfigurationSection s = cfg().getConfigurationSection("punishments.list");
        return s == null ? new ArrayList<>() : new ArrayList<>(s.getKeys(false));
    }

    public String punishmentName(String id) {
        return Text.color(cfg().getString("punishments.list." + id + ".name", id));
    }

    // ------------------------------------------------------------ doğrulama

    public Err validate(Player reporter, ReportDraft d) {
        long now = System.currentTimeMillis();
        UUID me = reporter.getUniqueId();
        if (d.target.uuid().equals(me)) {
            return new Err("msg.self-report", new String[0]);
        }
        Player online = Bukkit.getPlayer(d.target.uuid());
        if (online != null && online.hasPermission("rapor.bypass")) {
            return new Err("msg.target-immune", new String[]{"player", d.target.name()});
        }
        if (d.category == null || category(d.category) == null) {
            return new Err("msg.invalid-category", new String[0]);
        }
        int min = cfg().getInt("report.min-reason-length", 5);
        String reason = d.reason == null ? "" : d.reason;
        if (requireReason(d.category) && reason.length() < min) {
            return new Err("msg.reason-required", new String[]{"min", String.valueOf(min)});
        }
        if (reporter.hasPermission("rapor.cooldown.bypass")) {
            return null;
        }
        long cooldown = cfg().getInt("report.cooldown-seconds", 60) * 1000L;
        Long last = lastReport.get(me);
        if (last != null && now - last < cooldown) {
            long left = (cooldown - (now - last) + 999) / 1000;
            return new Err("msg.cooldown", new String[]{"seconds", String.valueOf(left)});
        }
        int max = cfg().getInt("report.max-per-hour", 5);
        if (max > 0) {
            Deque<Long> dq = hourly.computeIfAbsent(me, k -> new ArrayDeque<>());
            while (!dq.isEmpty() && now - dq.peekFirst() > 3_600_000L) {
                dq.pollFirst();
            }
            if (dq.size() >= max) {
                return new Err("msg.hourly-limit", new String[]{"max", String.valueOf(max)});
            }
        }
        Long pair = pairs.get(me + ":" + d.target.uuid());
        long dupMs = cfg().getInt("report.duplicate-minutes", 30) * 60_000L;
        if (pair != null && now - pair < dupMs) {
            return new Err("msg.duplicate", new String[]{"player", d.target.name()});
        }
        return null;
    }

    // --------------------------------------------------------- oluşturma

    public void submit(Player reporter, ReportDraft d) {
        Err err = validate(reporter, d);
        if (err != null) {
            messages().send(reporter, err.path(), err.kv());
            return;
        }
        long now = System.currentTimeMillis();
        UUID me = reporter.getUniqueId();
        lastReport.put(me, now);
        hourly.computeIfAbsent(me, k -> new ArrayDeque<>()).addLast(now);
        pairs.put(me + ":" + d.target.uuid(), now);

        Report r = new Report();
        r.reporterUuid = me;
        r.reporterName = reporter.getName();
        r.targetUuid = d.target.uuid();
        r.targetName = d.target.name();
        r.category = d.category;
        r.subType = d.subType;
        r.reason = d.reason == null || d.reason.isBlank() ? null : d.reason;
        r.createdAt = now;
        r.server = cfg().getString("server-name", "Sunucu");
        r.info = infoFor(r.targetUuid);
        finish(r, reporter);
    }

    /** Sistem adına (anti-cheat) rapor açar. Ana thread'den çağır. */
    public void createAuto(UUID uuid, String name, String category, String subType, String reason) {
        Report r = new Report();
        r.reporterUuid = AUTO_UUID;
        r.reporterName = Text.strip(messages().msg("msg.auto-reporter-name"));
        r.targetUuid = uuid;
        r.targetName = name;
        List<String> ids = categoryIds(false);
        r.category = category != null && category(category) != null ? category : (ids.isEmpty() ? "diger" : ids.get(0));
        r.subType = subType;
        r.reason = reason;
        r.createdAt = System.currentTimeMillis();
        r.auto = true;
        r.server = cfg().getString("server-name", "Sunucu");
        r.info = infoFor(uuid);
        finish(r, null);
    }

    /** Mesaj geçmişi ve flag'leri toplar, kaydeder, sonra herkese haber verir. */
    private void finish(Report r, Player reporter) {
        int size = Math.max(1, cfg().getInt("logging.history-size", 50));
        long since = System.currentTimeMillis() - cfg().getLong("anticheat.attach-flags-minutes", 30) * 60_000L;
        CompletableFuture<List<com.raporx.model.ChatEntry>> chatF = plugin.chatLog().recent(r.targetUuid, size);
        CompletableFuture<List<FlagEntry>> flagF = plugin.database().recentFlags(r.targetUuid, since, 50);
        chatF.thenCombine(flagF, (chat, flags) -> {
                    r.chat = chat;
                    r.flags = flags;
                    return r;
                })
                .thenCompose(rep -> plugin.database().insertReport(rep))
                .whenComplete((id, ex) -> plugin.sync(() -> {
                    if (ex != null || id == null || id < 0) {
                        if (reporter != null && reporter.isOnline()) {
                            messages().send(reporter, "msg.report-failed");
                        }
                        return;
                    }
                    r.id = id;
                    onCreated(r, reporter);
                }));
    }

    private void onCreated(Report r, Player reporter) {
        if (reporter != null && reporter.isOnline()) {
            messages().send(reporter, "msg.report-sent", "id", String.valueOf(r.id), "player", r.targetName);
        }
        notifyStaff(r);
        plugin.webhook().sendReport(r);
        plugin.getLogger().info("Yeni rapor #" + r.id + ": " + r.reporterName + " -> " + r.targetName
                + " (" + categoryPlain(r.category) + ")");
    }

    private String infoFor(UUID uuid) {
        Player t = Bukkit.getPlayer(uuid);
        if (t == null) {
            return Text.strip(messages().msg("msg.info-offline"));
        }
        Location l = t.getLocation();
        return Text.strip(messages().msg("msg.info-online",
                "world", l.getWorld() == null ? "?" : l.getWorld().getName(),
                "x", String.valueOf(l.getBlockX()), "y", String.valueOf(l.getBlockY()), "z", String.valueOf(l.getBlockZ()),
                "ping", String.valueOf(t.getPing()), "mode", t.getGameMode().name()));
    }

    // ------------------------------------------------------------- kanıt

    public Evidence evidence(Report r) {
        double sum = 0;
        double max = 0;
        Map<String, Double> perCheck = new HashMap<>();
        for (FlagEntry f : r.flags) {
            sum += f.vl();
            max = Math.max(max, f.vl());
            perCheck.merge(f.check(), f.vl(), Double::sum);
        }
        String top = perCheck.entrySet().stream().max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse("-");
        int level = r.flags.isEmpty() ? 0 : (sum >= cfg().getDouble("anticheat.high-vl", 20) ? 2 : 1);
        return new Evidence(r.flags.size(), sum, max, top, level);
    }

    /** Tek cümlelik kanıt özeti. colored=false ise renk kodsuz (Discord için). */
    public String evidenceSummary(Evidence ev, boolean colored) {
        String key = ev.level() == 2 ? "msg.evidence-high" : ev.level() == 1 ? "msg.evidence-low" : "msg.evidence-none";
        String text = messages().msg(key, "count", String.valueOf(ev.count()),
                "vl", String.format("%.1f", ev.sumVl()), "check", ev.topCheck());
        return colored ? text : Text.strip(text);
    }

    // ---------------------------------------------------------- bildirim

    private void notifyStaff(Report r) {
        Evidence ev = evidence(r);
        String line = messages().msg("msg.staff-alert", "id", String.valueOf(r.id), "target", r.targetName,
                "reporter", r.reporterName, "category", categoryName(r.category)
                        + (r.subType != null && !r.subType.isBlank() ? " › " + r.subType : ""));
        String evidence = isCheat(r.category) || ev.count() > 0 ? evidenceSummary(ev, true) : null;
        TextComponent button = new TextComponent(messages().msg("msg.staff-alert-button", "id", String.valueOf(r.id)));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/raporadmin ac " + r.id));
        boolean sound = cfg().getBoolean("notify.staff-sound", true);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasPermission("rapor.notify")) {
                continue;
            }
            p.sendMessage(messages().prefix() + line);
            if (evidence != null) {
                p.sendMessage(messages().prefix() + evidence);
            }
            p.spigot().sendMessage(button);
            if (sound) {
                p.playSound(p.getLocation(), "block.note_block.pling", 1f, 1.5f);
            }
        }
    }

    /** Rapor sahibine sonucu bildirir; çevrimdışıysa girişte gösterilmek üzere saklar. */
    private void notifyReporter(Report r) {
        if (r.auto || r.status == Report.Status.OPEN) {
            return;
        }
        String path = r.status == Report.Status.REJECTED ? "msg.reporter-rejected"
                : r.punishment != null ? "msg.reporter-punished" : "msg.reporter-resolved";
        String text = messages().msg(path, "id", String.valueOf(r.id), "player", r.targetName);
        Player p = Bukkit.getPlayer(r.reporterUuid);
        if (p != null) {
            p.sendMessage(messages().prefix() + text);
        } else {
            plugin.database().addNotification(r.reporterUuid, text).exceptionally(ex -> null);
        }
    }

    public void deliverNotifications(Player p) {
        plugin.database().popNotifications(p.getUniqueId()).thenAccept(list -> plugin.sync(() -> {
            if (!p.isOnline()) {
                return;
            }
            for (String m : list) {
                p.sendMessage(messages().prefix() + m);
            }
        })).exceptionally(ex -> null);
    }

    // ------------------------------------------------------------ işlemler

    public void setStatus(CommandSender admin, Report r, Report.Status status, String note, String punishment) {
        long now = System.currentTimeMillis();
        r.status = status;
        r.handledBy = admin.getName();
        r.handledAt = now;
        if (note != null) {
            r.note = note;
        }
        if (punishment != null) {
            r.punishment = punishment;
        }
        plugin.database().updateReport(r.id, status, r.handledBy, now, note, punishment).exceptionally(ex -> null);
        notifyReporter(r);
        plugin.webhook().sendAction(r, admin.getName());
    }

    public void reopen(CommandSender admin, Report r) {
        r.status = Report.Status.OPEN;
        r.handledBy = null;
        r.handledAt = 0;
        plugin.database().reopenReport(r.id).exceptionally(ex -> null);
        plugin.webhook().sendAction(r, admin.getName());
    }

    /**
     * Cezayı konsol komutu olarak çalıştırır (oyuncu çevrimdışı olsa da).
     * Komuta raporlayanın yazdığı metin ASLA girmez; sadece yetkilinin seçtiği sebep girer.
     */
    public boolean punish(Player admin, Report r, String punishmentId, String reason) {
        ConfigurationSection p = cfg().getConfigurationSection("punishments.list." + punishmentId);
        if (p == null) {
            messages().send(admin, "msg.punish-missing");
            return false;
        }
        if (r.targetUuid.equals(admin.getUniqueId())) {
            messages().send(admin, "msg.punish-self");
            return false;
        }
        if (!SAFE_NAME.matcher(r.targetName).matches()) {
            messages().send(admin, "msg.punish-bad-name", "player", r.targetName);
            return false;
        }
        String safeReason = Text.clean(reason, 100);
        for (String template : p.getStringList("commands")) {
            String cmd = template
                    .replace("%player%", r.targetName)
                    .replace("%uuid%", r.targetUuid.toString())
                    .replace("%reason%", safeReason)
                    .replace("%category%", categoryPlain(r.category))
                    .replace("%id%", String.valueOf(r.id))
                    .replace("%admin%", admin.getName());
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            plugin.getLogger().info(admin.getName() + " ceza komutu çalıştırdı: " + cmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
        String name = Text.strip(p.getString("name", punishmentId));
        setStatus(admin, r, Report.Status.RESOLVED, null, name);
        messages().send(admin, "msg.punish-applied", "player", r.targetName, "punishment", name);
        return true;
    }

    /** Ceza menüsünde öntanımlı sebep. */
    public String defaultPunishReason(Report r) {
        return cfg().getString("punishments.default-reason", "%category% - Rapor #%id%")
                .replace("%category%", categoryPlain(r.category))
                .replace("%id%", String.valueOf(r.id))
                .replace("%sub%", r.subType == null ? "" : r.subType);
    }
}
