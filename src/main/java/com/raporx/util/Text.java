package com.raporx.util;

import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Renk (&amp; ve &amp;#RRGGBB), small caps ve satır kaydırma yardımcıları. */
public final class Text {
    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final Pattern HEX6 = Pattern.compile("[A-Fa-f0-9]{6}");
    private static final Map<Character, Character> SMALL = new HashMap<>();
    private static volatile boolean smallCaps = true;

    static {
        String from = "abcdefghijklmnopqrstuvwxyzı";
        String to = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢɪ";
        if (from.length() != to.length()) {
            throw new IllegalStateException("small caps tablosu hatalı");
        }
        for (int i = 0; i < from.length(); i++) {
            SMALL.put(from.charAt(i), to.charAt(i));
        }
    }

    private Text() {
    }

    public static void setSmallCaps(boolean value) {
        smallCaps = value;
    }

    public static boolean smallCaps() {
        return smallCaps;
    }

    /** &amp;a ve &amp;#RRGGBB renk kodlarını çevirir. */
    public static String color(String s) {
        if (s == null) {
            return "";
        }
        Matcher m = HEX.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(ChatColor.of("#" + m.group(1)).toString()));
        }
        m.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    public static String strip(String s) {
        return s == null ? "" : ChatColor.stripColor(color(s));
    }

    /**
     * Metni small caps'e çevirir. Renk kodlarına ve {yer_tutucu} içlerine dokunmaz,
     * böylece oyuncu adı gibi değerler sonradan olduğu gibi yerleşir.
     */
    public static String toSmallCaps(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < n) {
                char next = s.charAt(i + 1);
                if (c == '&' && next == '#' && i + 8 <= n && HEX6.matcher(s.substring(i + 2, i + 8)).matches()) {
                    sb.append(s, i, i + 8);
                    i += 8;
                    continue;
                }
                sb.append(c).append(next);
                i += 2;
                continue;
            }
            if (c == '{') {
                int close = s.indexOf('}', i);
                if (close > i) {
                    sb.append(s, i, close + 1);
                    i = close + 1;
                    continue;
                }
            }
            Character mapped = SMALL.get(Character.toLowerCase(c));
            sb.append(mapped != null ? mapped : c);
            i++;
        }
        return sb.toString();
    }

    /** Oyuncudan gelen metni temizler: renk, kontrol karakteri ve fazla boşlukları atar. */
    public static String clean(String s, int max) {
        if (s == null) {
            return "";
        }
        String out = s.replace("§", "").replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
        if (max > 0 && out.length() > max) {
            out = out.substring(0, max).trim();
        }
        return out;
    }

    public static int visibleLength(String s) {
        return ChatColor.stripColor(s).length();
    }

    /** Uzun satırı kelime kelime böler; devam satırları beyaz başlar. */
    public static List<String> wrap(String line, int width) {
        List<String> out = new ArrayList<>();
        if (line == null) {
            return out;
        }
        if (visibleLength(line) <= width) {
            out.add(line);
            return out;
        }
        String prepared = line.replaceAll("(\\S{" + width + "})(?=\\S)", "$1 ");
        StringBuilder cur = new StringBuilder();
        int vis = 0;
        for (String word : prepared.split(" ")) {
            int wl = visibleLength(word);
            if (vis > 0 && vis + 1 + wl > width) {
                out.add(cur.toString());
                cur = new StringBuilder("§f");
                vis = 0;
            }
            if (vis > 0) {
                cur.append(' ');
                vis++;
            }
            cur.append(word);
            vis += wl;
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}
