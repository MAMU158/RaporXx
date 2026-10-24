package com.raporx.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.raporx.RaporX;
import com.raporx.model.ChatEntry;
import com.raporx.model.FlagEntry;
import com.raporx.model.Report;
import com.raporx.model.TargetRef;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * SQLite depolama (Spigot/Paper zaten sqlite-jdbc ile gelir, ekstra kütüphane gerekmez).
 * Tüm sorgular tek bir arka plan thread'inde çalışır; ana thread asla bloklanmaz.
 */
public final class Database {
    @FunctionalInterface
    public interface SqlFunc<T> {
        T apply(Connection c) throws SQLException;
    }

    private static final String[] SCHEMA = {
            "CREATE TABLE IF NOT EXISTS reports ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "reporter_uuid TEXT NOT NULL, reporter_name TEXT NOT NULL,"
                    + "target_uuid TEXT NOT NULL, target_name TEXT NOT NULL,"
                    + "category TEXT NOT NULL, sub_type TEXT, reason TEXT,"
                    + "created_at INTEGER NOT NULL, status TEXT NOT NULL DEFAULT 'OPEN',"
                    + "handled_by TEXT, handled_at INTEGER, note TEXT, punishment TEXT,"
                    + "chat_json TEXT, flags_json TEXT, auto INTEGER DEFAULT 0, server TEXT, info TEXT)",
            "CREATE INDEX IF NOT EXISTS idx_reports_target ON reports(target_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_reports_reporter ON reports(reporter_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status)",
            "CREATE TABLE IF NOT EXISTS chat_log ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "uuid TEXT NOT NULL, name TEXT NOT NULL, ts INTEGER NOT NULL, msg TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_chat_uuid ON chat_log(uuid, id)",
            "CREATE TABLE IF NOT EXISTS ac_flags ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "uuid TEXT NOT NULL, name TEXT NOT NULL, check_name TEXT NOT NULL,"
                    + "vl REAL NOT NULL, details TEXT, ts INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_flags_uuid ON ac_flags(uuid, ts)",
            "CREATE TABLE IF NOT EXISTS notifications ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "uuid TEXT NOT NULL, message TEXT NOT NULL, ts INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_notif_uuid ON notifications(uuid)",
            "CREATE TABLE IF NOT EXISTS players ("
                    + "uuid TEXT PRIMARY KEY, name TEXT NOT NULL COLLATE NOCASE, last_seen INTEGER NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_players_name ON players(name)"
    };

    private final RaporX plugin;
    private Connection conn;
    private final ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RaporX-DB");
        t.setDaemon(true);
        return t;
    });

    public Database(RaporX plugin) {
        this.plugin = plugin;
    }

    public void open() throws Exception {
        File dir = plugin.getDataFolder();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Plugin klasörü oluşturulamadı: " + dir);
        }
        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:" + new File(dir, "rapor.db").getAbsolutePath());
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            for (String sql : SCHEMA) {
                st.execute(sql);
            }
        }
    }

    public void close() {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(5, TimeUnit.SECONDS)) {
                exec.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException ignored) {
            // kapanırken önemsiz
        }
    }

    public <T> CompletableFuture<T> call(SqlFunc<T> f) {
        if (exec.isShutdown()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Veritabanı kapalı"));
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                return f.apply(conn);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Veritabanı hatası: " + e.getMessage(), e);
                throw new CompletionException(e);
            }
        }, exec);
    }

    // ---------------------------------------------------------------- sohbet

    public CompletableFuture<Void> insertChat(UUID uuid, String name, long ts, String msg) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO chat_log(uuid,name,ts,msg) VALUES(?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setLong(3, ts);
                ps.setString(4, msg);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Son n mesaj, eskiden yeniye sıralı. */
    public CompletableFuture<List<ChatEntry>> recentChat(UUID uuid, int n) {
        return call(c -> {
            List<ChatEntry> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT ts,msg FROM chat_log WHERE uuid=? ORDER BY id DESC LIMIT ?")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, n);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new ChatEntry(rs.getLong(1), rs.getString(2)));
                    }
                }
            }
            Collections.reverse(list);
            return list;
        });
    }

    public CompletableFuture<Void> pruneChat(int keep) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM chat_log WHERE id IN (SELECT id FROM ("
                            + "SELECT id, ROW_NUMBER() OVER (PARTITION BY uuid ORDER BY id DESC) AS rn FROM chat_log"
                            + ") WHERE rn > ?)")) {
                ps.setInt(1, keep);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ----------------------------------------------------------------- flag

    public CompletableFuture<Void> insertFlag(UUID uuid, String name, String check, double vl, String details, long ts) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ac_flags(uuid,name,check_name,vl,details,ts) VALUES(?,?,?,?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setString(3, check);
                ps.setDouble(4, vl);
                ps.setString(5, details);
                ps.setLong(6, ts);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** since'den yeni flag'ler, yeniden eskiye sıralı. */
    public CompletableFuture<List<FlagEntry>> recentFlags(UUID uuid, long since, int limit) {
        return call(c -> {
            List<FlagEntry> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ts,check_name,vl,details FROM ac_flags WHERE uuid=? AND ts>=? ORDER BY id DESC LIMIT ?")) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, since);
                ps.setInt(3, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new FlagEntry(rs.getLong(1), rs.getString(2), rs.getDouble(3), rs.getString(4)));
                    }
                }
            }
            return list;
        });
    }

    public CompletableFuture<Void> pruneFlags(long before) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM ac_flags WHERE ts<?")) {
                ps.setLong(1, before);
                ps.executeUpdate();
            }
            return null;
        });
    }

    // ---------------------------------------------------------------- rapor

    public CompletableFuture<Integer> insertReport(Report r) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO reports(reporter_uuid,reporter_name,target_uuid,target_name,category,sub_type,reason,"
                            + "created_at,status,chat_json,flags_json,auto,server,info) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, r.reporterUuid.toString());
                ps.setString(2, r.reporterName);
                ps.setString(3, r.targetUuid.toString());
                ps.setString(4, r.targetName);
                ps.setString(5, r.category);
                ps.setString(6, r.subType);
                ps.setString(7, r.reason);
                ps.setLong(8, r.createdAt);
                ps.setString(9, r.status.name());
                ps.setString(10, chatToJson(r.chat));
                ps.setString(11, flagsToJson(r.flags));
                ps.setInt(12, r.auto ? 1 : 0);
                ps.setString(13, r.server);
                ps.setString(14, r.info);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    return keys.next() ? keys.getInt(1) : -1;
                }
            }
        });
    }

    public CompletableFuture<Report> getReport(int id) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM reports WHERE id=?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? readReport(rs, true) : null;
                }
            }
        });
    }

    /** status null ise hepsi. En yeni başta. */
    public CompletableFuture<List<Report>> listReports(Report.Status status, int limit) {
        return call(c -> {
            String sql = "SELECT * FROM reports" + (status != null ? " WHERE status=?" : "") + " ORDER BY id DESC LIMIT ?";
            List<Report> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                int i = 1;
                if (status != null) {
                    ps.setString(i++, status.name());
                }
                ps.setInt(i, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(readReport(rs, false));
                    }
                }
            }
            return list;
        });
    }

    public CompletableFuture<List<Report>> listReportsByReporter(UUID reporter, int limit) {
        return call(c -> {
            List<Report> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM reports WHERE reporter_uuid=? ORDER BY id DESC LIMIT ?")) {
                ps.setString(1, reporter.toString());
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(readReport(rs, false));
                    }
                }
            }
            return list;
        });
    }

    public CompletableFuture<Void> updateReport(int id, Report.Status status, String handledBy, long handledAt,
                                                String note, String punishment) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE reports SET status=?, handled_by=?, handled_at=?, note=COALESCE(?, note), "
                            + "punishment=COALESCE(?, punishment) WHERE id=?")) {
                ps.setString(1, status.name());
                ps.setString(2, handledBy);
                ps.setLong(3, handledAt);
                ps.setString(4, note);
                ps.setString(5, punishment);
                ps.setInt(6, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Void> reopenReport(int id) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE reports SET status='OPEN', handled_by=NULL, handled_at=NULL WHERE id=?")) {
                ps.setInt(1, id);
                ps.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Integer> countAgainst(UUID target) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM reports WHERE target_uuid=?")) {
                ps.setString(1, target.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    public CompletableFuture<Integer> countByStatus(Report.Status status) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM reports WHERE status=?")) {
                ps.setString(1, status.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        });
    }

    // ------------------------------------------------------------ bildirim

    public CompletableFuture<Void> addNotification(UUID uuid, String message) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO notifications(uuid,message,ts) VALUES(?,?,?)")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, message);
                ps.setLong(3, System.currentTimeMillis());
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** Bekleyen bildirimleri okur ve siler. */
    public CompletableFuture<List<String>> popNotifications(UUID uuid) {
        return call(c -> {
            List<String> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT message FROM notifications WHERE uuid=? ORDER BY id")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(rs.getString(1));
                    }
                }
            }
            if (!list.isEmpty()) {
                try (PreparedStatement del = c.prepareStatement("DELETE FROM notifications WHERE uuid=?")) {
                    del.setString(1, uuid.toString());
                    del.executeUpdate();
                }
            }
            return list;
        });
    }

    // -------------------------------------------------------------- oyuncu

    public CompletableFuture<Void> upsertPlayer(UUID uuid, String name, long ts) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO players(uuid,name,last_seen) VALUES(?,?,?) "
                            + "ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, last_seen=excluded.last_seen")) {
                ps.setString(1, uuid.toString());
                ps.setString(2, name);
                ps.setLong(3, ts);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** İlk kurulumda mevcut oyuncuları içeri alır; zaten kayıtlı olanlara dokunmaz. */
    public CompletableFuture<Void> seedPlayers(List<TargetRef> players, List<Long> lastSeen) {
        return call(c -> {
            boolean auto = c.getAutoCommit();
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO players(uuid,name,last_seen) VALUES(?,?,?) ON CONFLICT(uuid) DO NOTHING")) {
                for (int i = 0; i < players.size(); i++) {
                    ps.setString(1, players.get(i).uuid().toString());
                    ps.setString(2, players.get(i).name());
                    ps.setLong(3, lastSeen.get(i));
                    ps.addBatch();
                }
                ps.executeBatch();
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(auto);
            }
            return null;
        });
    }

    /** İsimden oyuncu bulur (büyük/küçük harf fark etmez). Yoksa null. */
    public CompletableFuture<TargetRef> findPlayer(String name) {
        return call(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT uuid,name FROM players WHERE name=? COLLATE NOCASE LIMIT 1")) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? new TargetRef(UUID.fromString(rs.getString(1)), rs.getString(2)) : null;
                }
            }
        });
    }

    public CompletableFuture<Integer> countPlayers() {
        return call(c -> {
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM players")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        });
    }

    // ----------------------------------------------------------------- json

    private static Report readReport(ResultSet rs, boolean full) throws SQLException {
        Report r = new Report();
        r.id = rs.getInt("id");
        r.reporterUuid = UUID.fromString(rs.getString("reporter_uuid"));
        r.reporterName = rs.getString("reporter_name");
        r.targetUuid = UUID.fromString(rs.getString("target_uuid"));
        r.targetName = rs.getString("target_name");
        r.category = rs.getString("category");
        r.subType = rs.getString("sub_type");
        r.reason = rs.getString("reason");
        r.createdAt = rs.getLong("created_at");
        try {
            r.status = Report.Status.valueOf(rs.getString("status"));
        } catch (IllegalArgumentException e) {
            r.status = Report.Status.OPEN;
        }
        r.handledBy = rs.getString("handled_by");
        r.handledAt = rs.getLong("handled_at");
        r.note = rs.getString("note");
        r.punishment = rs.getString("punishment");
        r.flags = flagsFromJson(rs.getString("flags_json"));
        if (full) {
            r.chat = chatFromJson(rs.getString("chat_json"));
        }
        r.auto = rs.getInt("auto") == 1;
        r.server = rs.getString("server");
        r.info = rs.getString("info");
        return r;
    }

    private static String chatToJson(List<ChatEntry> list) {
        JsonArray arr = new JsonArray();
        for (ChatEntry e : list) {
            JsonObject o = new JsonObject();
            o.addProperty("t", e.time());
            o.addProperty("m", e.message());
            arr.add(o);
        }
        return arr.toString();
    }

    private static List<ChatEntry> chatFromJson(String json) {
        List<ChatEntry> list = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return list;
        }
        try {
            for (JsonElement el : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                list.add(new ChatEntry(o.get("t").getAsLong(), o.get("m").getAsString()));
            }
        } catch (RuntimeException ignored) {
            // bozuk kayıt: boş liste dön
        }
        return list;
    }

    private static String flagsToJson(List<FlagEntry> list) {
        JsonArray arr = new JsonArray();
        for (FlagEntry e : list) {
            JsonObject o = new JsonObject();
            o.addProperty("t", e.time());
            o.addProperty("c", e.check());
            o.addProperty("v", e.vl());
            o.addProperty("d", e.details() == null ? "" : e.details());
            arr.add(o);
        }
        return arr.toString();
    }

    private static List<FlagEntry> flagsFromJson(String json) {
        List<FlagEntry> list = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return list;
        }
        try {
            for (JsonElement el : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject o = el.getAsJsonObject();
                list.add(new FlagEntry(o.get("t").getAsLong(), o.get("c").getAsString(),
                        o.get("v").getAsDouble(), o.get("d").getAsString()));
            }
        } catch (RuntimeException ignored) {
            // bozuk kayıt: boş liste dön
        }
        return list;
    }
}
