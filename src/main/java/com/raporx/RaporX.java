package com.raporx;

import com.raporx.command.RaporAdminCommand;
import com.raporx.command.RaporCommand;
import com.raporx.gui.MenuListener;
import com.raporx.listener.ChatListener;
import com.raporx.listener.JoinListener;
import com.raporx.manager.ChatLogManager;
import com.raporx.manager.FlagManager;
import com.raporx.manager.InputManager;
import com.raporx.manager.PlayerDirectory;
import com.raporx.manager.ReportManager;
import com.raporx.manager.WebhookManager;
import com.raporx.storage.Database;
import com.raporx.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class RaporX extends JavaPlugin {
    private static RaporX instance;

    private Messages messages;
    private Database database;
    private PlayerDirectory directory;
    private ChatLogManager chatLog;
    private FlagManager flags;
    private WebhookManager webhook;
    private ReportManager reports;
    private InputManager input;

    public static RaporX getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        messages = new Messages(this);
        applyStyle();

        database = new Database(this);
        try {
            database.open();
        } catch (Exception e) {
            getLogger().severe("Veritabanı açılamadı, plugin kapatılıyor: " + e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        directory = new PlayerDirectory(this);
        chatLog = new ChatLogManager(this);
        flags = new FlagManager(this);
        webhook = new WebhookManager(this);
        reports = new ReportManager(this);
        input = new InputManager(this);

        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);
        Bukkit.getPluginManager().registerEvents(input, this);
        Bukkit.getPluginManager().registerEvents(new ChatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new JoinListener(this), this);

        register("rapor", new RaporCommand(this));
        register("raporadmin", new RaporAdminCommand(this));

        long interval = Math.max(1, getConfig().getLong("logging.prune-interval-minutes", 10)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, chatLog::prune, interval, interval);
        directory.seedIfEmpty();

        getLogger().info("RaporX hazır. Discord webhook: " + (webhook.enabled() ? "açık" : "kapalı"));
    }

    @Override
    public void onDisable() {
        if (webhook != null) {
            webhook.shutdown();
        }
        if (database != null) {
            database.close();
        }
    }

    private void register(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("plugin.yml içinde '" + name + "' komutu bulunamadı!");
            return;
        }
        cmd.setExecutor((org.bukkit.command.CommandExecutor) executor);
        if (executor instanceof org.bukkit.command.TabCompleter tc) {
            cmd.setTabCompleter(tc);
        }
    }

    /** config.yml + messages.yml'i yeniden okur. */
    public void reload() {
        reloadConfig();
        messages.reload();
        applyStyle();
    }

    public void applyStyle() {
        Text.setSmallCaps(getConfig().getBoolean("style.small-caps", true));
    }

    /** Ana thread'de çalıştırır (zaten ana thread'deyse hemen). */
    public void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(this, task);
        }
    }

    public Messages messages() {
        return messages;
    }

    public Database database() {
        return database;
    }

    public PlayerDirectory directory() {
        return directory;
    }

    public ChatLogManager chatLog() {
        return chatLog;
    }

    public FlagManager flags() {
        return flags;
    }

    public WebhookManager webhook() {
        return webhook;
    }

    public ReportManager reports() {
        return reports;
    }

    public InputManager input() {
        return input;
    }
}
