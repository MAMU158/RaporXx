package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.manager.WebhookManager;
import com.raporx.model.Report;
import com.raporx.util.ItemBuilder;
import com.raporx.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Oyun içi ayar paneli: değişiklikler config.yml'e yazılır. */
public final class SettingsMenu extends Menu {

    public SettingsMenu(RaporX plugin, Player viewer) {
        super(plugin, viewer);
    }

    @Override
    protected String title() {
        return msgs().ui("menu.settings.title");
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    protected void build() {
        if (!viewer.hasPermission("rapor.settings")) {
            viewer.closeInventory();
            return;
        }
        toggle(10, "discord.enabled", "discord-enabled");
        toggle(11, "discord.send-chat-file", "discord-file");
        webhookUrlButton(12);
        set(13, button(Material.BELL, "menu.settings.webhook-test"), e -> {
            viewer.closeInventory();
            plugin.messages().send(viewer, "msg.webhook-testing");
            plugin.webhook().sendTest().thenAccept(code -> plugin.sync(() -> {
                if (!viewer.isOnline()) {
                    return;
                }
                if (code != null && code >= 200 && code < 300) {
                    plugin.messages().send(viewer, "msg.webhook-ok");
                } else {
                    plugin.messages().send(viewer, "msg.webhook-fail", "code", String.valueOf(code));
                }
            }));
        });

        number(19, "report.cooldown-seconds", "cooldown", 0, 3600, 5, Material.CLOCK);
        number(20, "report.max-per-hour", "max-per-hour", 0, 100, 1, Material.HOPPER);
        toggle(21, "logging.log-commands", "log-commands");
        toggle(22, "style.small-caps", "small-caps");
        toggle(23, "notify.staff-sound", "staff-sound");
        toggle(24, "anticheat.auto-report.enabled", "auto-report");
        number(25, "anticheat.auto-report.vl-threshold", "auto-threshold", 1, 1000, 5, Material.REDSTONE);

        set(29, button(Material.CHEST, "menu.settings.categories"), e -> new CategoryEditorMenu(plugin, viewer).open());
        set(31, button(Material.SUNFLOWER, "menu.settings.reload"), e -> {
            plugin.reload();
            plugin.messages().send(viewer, "msg.reloaded");
            new SettingsMenu(plugin, viewer).open();
        });

        addBack(36, () -> AdminPanelMenu.openFor(plugin, viewer, Report.Status.OPEN));
        addClose(40);
    }

    private void toggle(int slot, String path, String key) {
        boolean on = plugin.getConfig().getBoolean(path, false);
        List<String> lore = new ArrayList<>(msgs().uiList("menu.settings." + key + ".lore"));
        lore.add(" ");
        lore.add(msgs().ui(on ? "menu.settings.state-on" : "menu.settings.state-off"));
        lore.add(msgs().ui("menu.settings.click-toggle"));
        set(slot, new ItemBuilder(on ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(msgs().ui("menu.settings." + key + ".name"))
                .lore(lore)
                .glow(on)
                .build(), e -> {
            plugin.getConfig().set(path, !on);
            plugin.saveConfig();
            plugin.applyStyle();
            new SettingsMenu(plugin, viewer).open();
        });
    }

    private void number(int slot, String path, String key, int min, int max, int step, Material icon) {
        int value = plugin.getConfig().getInt(path, 0);
        List<String> lore = new ArrayList<>(msgs().uiList("menu.settings." + key + ".lore"));
        lore.add(" ");
        lore.add(msgs().ui("menu.settings.value", "value", String.valueOf(value)));
        lore.add(msgs().ui("menu.settings.click-number", "step", String.valueOf(step), "big", String.valueOf(step * 5)));
        set(slot, new ItemBuilder(icon)
                .name(msgs().ui("menu.settings." + key + ".name"))
                .lore(lore)
                .build(), e -> {
            int delta = (e.isShiftClick() ? step * 5 : step) * (e.isRightClick() ? -1 : 1);
            int next = Math.max(min, Math.min(max, value + delta));
            plugin.getConfig().set(path, next);
            plugin.saveConfig();
            new SettingsMenu(plugin, viewer).open();
        });
    }

    private void webhookUrlButton(int slot) {
        String url = plugin.getConfig().getString("discord.webhook-url", "");
        String shown = url == null || url.isBlank() ? "—"
                : url.length() > 34 ? url.substring(0, 34) + "…" : url;
        List<String> lore = new ArrayList<>(msgs().uiList("menu.settings.webhook-url.lore"));
        lore.add(" ");
        lore.add(msgs().ui("menu.settings.value", "value", shown));
        set(slot, new ItemBuilder(Material.WRITABLE_BOOK)
                .name(msgs().ui("menu.settings.webhook-url.name"))
                .lore(lore)
                .build(), e -> plugin.input().request(viewer, "msg.input-webhook", text -> {
            String typed = text.trim();
            if (typed.equals("-")) {
                plugin.getConfig().set("discord.webhook-url", "");
                plugin.saveConfig();
                plugin.messages().send(viewer, "msg.webhook-cleared");
            } else if (WebhookManager.isValidUrl(typed)) {
                plugin.getConfig().set("discord.webhook-url", typed);
                plugin.saveConfig();
                plugin.messages().send(viewer, "msg.webhook-saved");
            } else {
                plugin.messages().send(viewer, "msg.webhook-invalid");
            }
            if (viewer.isOnline()) {
                new SettingsMenu(plugin, viewer).open();
            }
        }));
    }
}
