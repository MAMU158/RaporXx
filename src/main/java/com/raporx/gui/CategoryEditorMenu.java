package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.util.ItemBuilder;
import com.raporx.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Rapor kategorilerini listeler; düzenleme ve yeni kategori ekleme. */
public final class CategoryEditorMenu extends PagedMenu<String> {
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_-]{2,20}");

    public CategoryEditorMenu(RaporX plugin, Player viewer) {
        super(plugin, viewer, plugin.reports().categoryIds(false));
    }

    @Override
    protected String title() {
        return msgs().ui("menu.cat-editor.title");
    }

    @Override
    protected ItemStack render(String id) {
        ConfigurationSection s = plugin.reports().category(id);
        boolean enabled = s != null && s.getBoolean("enabled", true);
        boolean cheat = s != null && s.getBoolean("cheat", false);
        return ItemBuilder.of(s == null ? null : s.getString("icon"), Material.PAPER)
                .name(plugin.reports().categoryName(id))
                .lore(msgs().uiList("menu.cat-editor.item.lore",
                        "id", id,
                        "enabled", msgs().ui(enabled ? "menu.settings.state-on" : "menu.settings.state-off"),
                        "cheat", msgs().ui(cheat ? "menu.settings.state-on" : "menu.settings.state-off")))
                .glow(enabled)
                .build();
    }

    @Override
    protected void onItemClick(String id, InventoryClickEvent event) {
        new CategoryEditMenu(plugin, viewer, id).open();
    }

    @Override
    protected void bottom() {
        addBack(46, () -> new SettingsMenu(plugin, viewer).open());
        set(47, button(Material.NETHER_STAR, "menu.cat-editor.new"),
                e -> plugin.input().request(viewer, "msg.input-category-id", this::create));
    }

    private void create(String typed) {
        String id = typed.trim().toLowerCase(Locale.ROOT);
        if (!VALID_ID.matcher(id).matches()) {
            plugin.messages().send(viewer, "msg.category-bad-id");
            new CategoryEditorMenu(plugin, viewer).open();
            return;
        }
        if (plugin.reports().category(id) != null) {
            plugin.messages().send(viewer, "msg.category-exists", "id", id);
            new CategoryEditorMenu(plugin, viewer).open();
            return;
        }
        String base = "categories." + id;
        plugin.getConfig().set(base + ".name", "&#FFFFFF" + id);
        plugin.getConfig().set(base + ".icon", "PAPER");
        plugin.getConfig().set(base + ".enabled", true);
        plugin.getConfig().set(base + ".cheat", false);
        plugin.getConfig().set(base + ".require-reason", false);
        plugin.getConfig().set(base + ".lore", new ArrayList<String>(List.of("&7" + Text.clean(id, 20))));
        plugin.saveConfig();
        plugin.messages().send(viewer, "msg.category-created", "id", id);
        new CategoryEditMenu(plugin, viewer, id).open();
    }
}
