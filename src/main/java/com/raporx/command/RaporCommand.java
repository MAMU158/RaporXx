package com.raporx.command;

import com.raporx.RaporX;
import com.raporx.gui.PlayerSelectMenu;
import com.raporx.gui.CategoryMenu;
import com.raporx.model.ReportDraft;
import com.raporx.util.Text;
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

/**
 * /rapor                  -> oyuncu seçme menüsü
 * /rapor &lt;oyuncu&gt;          -> kategori menüsü (çevrimdışı oyuncu da olur)
 * /rapor &lt;oyuncu&gt; &lt;sebep&gt; -> hızlı rapor (config'teki quick-category)
 */
public final class RaporCommand implements CommandExecutor, TabCompleter {
    private final RaporX plugin;

    public RaporCommand(RaporX plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "msg.players-only");
            return true;
        }
        if (!player.hasPermission("rapor.use")) {
            plugin.messages().send(player, "msg.no-permission");
            return true;
        }
        if (args.length == 0) {
            new PlayerSelectMenu(plugin, player).open();
            return true;
        }
        String name = Text.clean(args[0], 32);
        String typedReason = args.length > 1
                ? Text.clean(String.join(" ", Arrays.copyOfRange(args, 1, args.length)),
                plugin.getConfig().getInt("report.max-reason-length", 200))
                : null;

        plugin.directory().find(name).whenComplete((ref, ex) -> plugin.sync(() -> {
            if (!player.isOnline()) {
                return;
            }
            if (ref == null) {
                plugin.messages().send(player, "msg.player-not-found", "player", name);
                return;
            }
            ReportDraft draft = new ReportDraft(ref);
            if (typedReason == null || typedReason.isEmpty()) {
                new CategoryMenu(plugin, player, draft).open();
                return;
            }
            String quick = plugin.getConfig().getString("report.quick-category", "diger");
            if (plugin.reports().category(quick) == null) {
                List<String> ids = plugin.reports().categoryIds(true);
                if (ids.isEmpty()) {
                    plugin.messages().send(player, "msg.invalid-category");
                    return;
                }
                quick = ids.get(0);
            }
            draft.category = quick;
            draft.reason = typedReason;
            plugin.reports().submit(player, draft);
        }));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player self && self.getUniqueId().equals(p.getUniqueId())) {
                continue;
            }
            if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add(p.getName());
            }
        }
        return out;
    }
}
