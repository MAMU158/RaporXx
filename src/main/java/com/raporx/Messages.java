package com.raporx;

import com.raporx.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * messages.yml okuyucusu.
 * msg*  : normal metin (sohbet mesajları)
 * ui*   : menü yazıları (small caps açıksa küçük büyük harfe çevrilir)
 * Yer tutucular {anahtar} biçimindedir ve renk/small caps işleminden SONRA doldurulur.
 */
public final class Messages {
    private final RaporX plugin;
    private YamlConfiguration cfg = new YamlConfiguration();

    public Messages(RaporX plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        cfg = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = plugin.getResource("messages.yml")) {
            if (in != null) {
                cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
            // varsayılanlar yüklenemezse dosyadaki değerler kullanılır
        }
    }

    public String raw(String path) {
        String s = cfg.getString(path);
        return s == null ? "&c[" + path + "]" : s;
    }

    public String prefix() {
        return Text.color(cfg.getString("prefix", ""));
    }

    public String msg(String path, String... kv) {
        return format(raw(path), false, kv);
    }

    public String ui(String path, String... kv) {
        return format(raw(path), Text.smallCaps(), kv);
    }

    public List<String> msgList(String path, String... kv) {
        return formatList(path, false, kv);
    }

    public List<String> uiList(String path, String... kv) {
        return formatList(path, Text.smallCaps(), kv);
    }

    public void send(CommandSender to, String path, String... kv) {
        to.sendMessage(prefix() + msg(path, kv));
    }

    private List<String> formatList(String path, boolean smallCaps, String... kv) {
        List<String> out = new ArrayList<>();
        for (String line : cfg.getStringList(path)) {
            out.add(format(line, smallCaps, kv));
        }
        return out;
    }

    private String format(String template, boolean smallCaps, String... kv) {
        String s = smallCaps ? Text.toSmallCaps(template) : template;
        s = Text.color(s);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", kv[i + 1] == null ? "" : kv[i + 1]);
        }
        return s;
    }
}
