package com.raporx.command;

import com.raporx.RaporX;
import com.raporx.gui.AdminPanelMenu;
import com.raporx.gui.ChatHistoryMenu;
import com.raporx.gui.ReportDetailMenu;
import com.raporx.gui.SettingsMenu;
import com.raporx.model.ChatEntry;
import com.raporx.model.Report;
import com.raporx.util.Text;
import com.raporx.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /raporadmin: panel, rapor açma, liste, mesaj geçmişi, flag ekleme, ayarlar. */
public final class RaporAdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("panel", "ac", "liste", "gecmis", "flag", "ayarlar", "reload", "test");

    private final RaporX plugin;

    public RaporAdminCommand(RaporX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "panel" : args[0].toLowerCase(Locale.ROOT);

        // Konsoldan da çalışabilen alt komutlar: flag, reload, liste, gecmis, test
        boolean consoleOk = sub.equals("flag") || sub.equals("reload") || sub.equals("liste")
                || sub.equals("gecmis") || sub.equals("test");
        if (!(sender instanceof Player) && !consoleOk) {
            plugin.messages().send(sender, "msg.players-only");
            return true;
        }
        String needed = sub.equals("flag") ? "rapor.flag" : sub.equals("ayarlar") || sub.equals("reload") || sub.equals("test")
                ? "rapor.settings" : "rapor.admin";
        if (!sender.hasPermission(needed)) {
            plugin.messages().send(sender, "msg.no-permission");
            return true;
        }

        switch (sub) {
            case "panel" -> AdminPanelMenu.openFor(plugin, (Player) sender, Report.Status.OPEN);
            case "ac" -> open(sender, args);
            case "liste" -> list(sender);
            case "gecmis" -> history(sender, args);
            case "flag" -> flag(sender, args);
            case "ayarlar" -> new SettingsMenu(plugin, (Player) sender).open();
            case "reload" -> {
                plugin.reload();
                plugin.messages().send(sender, "msg.reloaded");
            }
            case "test" -> {
                plugin.messages().send(sender, "msg.webhook-testing");
                plugin.webhook().sendTest().thenAccept(code -> plugin.sync(() ->
                        plugin.messages().send(sender, code != null && code >= 200 && code < 300
                                ? "msg.webhook-ok" : "msg.webhook-fail", "code", String.valueOf(code))));
            }
            default -> plugin.messages().send(sender, "msg.usage-admin");
        }
        return true;
    }

    private void open(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return;
        }
        if (args.length < 2) {
            plugin.messages().send(sender, "msg.usage-open");
            return;
        }
        try {
            ReportDetailMenu.openFor(plugin, player, Integer.parseInt(args[1].replace("#", "")));
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "msg.invalid-number", "value", args[1]);
        }
    }

    private void list(CommandSender sender) {
        plugin.database().listReports(Report.Status.OPEN, 15).whenComplete((reports, ex) -> plugin.sync(() -> {
            if (ex != null || reports == null) {
                plugin.messages().send(sender, "msg.load-failed");
                return;
            }
            if (reports.isEmpty()) {
                plugin.messages().send(sender, "msg.list-empty");
                return;
            }
            plugin.messages().send(sender, "msg.list-header", "count", String.valueOf(reports.size()));
            for (Report r : reports) {
                sender.sendMessage(plugin.messages().msg("msg.list-line",
                        "id", String.valueOf(r.id), "reporter", r.reporterName, "target", r.targetName,
                        "category", plugin.reports().categoryPlain(r.category),
                        "status", plugin.reports().statusText(r.status)));
            }
        }));
    }

    /** Oyuncu çevrimdışı olsa bile son mesajlarını gösterir. */
    private void history(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.messages().send(sender, "msg.usage-history");
            return;
        }
        String name = Text.clean(args[1], 32);
        int size = Math.max(1, plugin.getConfig().getInt("logging.history-size", 50));
        plugin.directory().find(name).thenCompose(ref -> {
            if (ref == null) {
                return java.util.concurrent.CompletableFuture.completedFuture(new Object[]{null, null});
            }
            return plugin.chatLog().recent(ref.uuid(), size).thenApply(list -> new Object[]{ref, list});
        }).whenComplete((pair, ex) -> plugin.sync(() -> {
            if (ex != null || pair == null) {
                plugin.messages().send(sender, "msg.load-failed");
                return;
            }
            if (pair[0] == null) {
                plugin.messages().send(sender, "msg.player-not-found", "player", name);
                return;
            }
            com.raporx.model.TargetRef ref = (com.raporx.model.TargetRef) pair[0];
            @SuppressWarnings("unchecked")
            List<ChatEntry> list = (List<ChatEntry>) pair[1];
            if (sender instanceof Player p) {
                new ChatHistoryMenu(plugin, p, list, ref.name(), p::closeInventory).open();
            } else {
                plugin.messages().send(sender, "msg.history-header", "player", ref.name(), "count", String.valueOf(list.size()));
                for (ChatEntry c : list) {
                    sender.sendMessage(plugin.messages().msg("msg.history-line",
                            "time", TimeUtil.clock(c.time()), "message", c.message()));
                }
            }
        }));
    }

    /** Hile korumasından flag kaydı: /raporadmin flag &lt;oyuncu&gt; &lt;kontrol&gt; &lt;vl&gt; [detay]. */
    private void flag(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.messages().send(sender, "msg.usage-flag");
            return;
        }
        double vl;
        try {
            vl = Double.parseDouble(args[3].replace(',', '.'));
        } catch (NumberFormatException e) {
            plugin.messages().send(sender, "msg.invalid-number", "value", args[3]);
            return;
        }
        String name = Text.clean(args[1], 32);
        String check = Text.clean(args[2], 40);
        String details = Text.clean(String.join(" ", Arrays.copyOfRange(args, 4, args.length)), 200);
        plugin.directory().find(name).whenComplete((ref, ex) -> plugin.sync(() -> {
            if (ref == null) {
                plugin.messages().send(sender, "msg.player-not-found", "player", name);
                return;
            }
            plugin.flags().add(ref.uuid(), ref.name(), check, vl, details);
            plugin.messages().send(sender, "msg.flag-added", "player", ref.name(), "check", check,
                    "vl", String.format("%.1f", vl));
        }));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            for (String s : SUBS) {
                if (s.startsWith(p)) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("gecmis") || args[0].equalsIgnoreCase("flag"))) {
            String p = args[1].toLowerCase(Locale.ROOT);
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(p)) {
                    out.add(pl.getName());
                }
            }
        }
        return out;
    }
}
