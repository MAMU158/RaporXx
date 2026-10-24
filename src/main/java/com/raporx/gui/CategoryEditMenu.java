package com.raporx.gui;

import com.raporx.RaporX;
import com.raporx.util.ItemBuilder;
import com.raporx.util.Text;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Tek bir kategorinin ikon, ad, açık/kapalı, hile ve alt tür ayarları. */
public final class CategoryEditMenu extends Menu {
    private static final int MAX_TYPES = 14;

    private final String id;

    public CategoryEditMenu(RaporX plugin, Player viewer, String id) {
        super(plugin, viewer);
        this.id = id;
    }

    @Override
    protected String title() {
        return msgs().ui("menu.cat-edit.title", "id", id);
    }

    @Override
    protected int rows() {
        return 4;
    }

    private String path(String key) {
        return "categories." + id + "." + key;
    }

    @Override
    protected void build() {
        ConfigurationSection s = plugin.reports().category(id);
        if (s == null) {
            new CategoryEditorMenu(plugin, viewer).open();
            return;
        }
        boolean enabled = s.getBoolean("enabled", true);
        boolean cheat = s.getBoolean("cheat", false);
        boolean needReason = s.getBoolean("require-reason", false);

        set(10, ItemBuilder.of(s.getString("icon"), Material.PAPER)
                .name(msgs().ui("menu.cat-edit.icon.name"))
                .lore(msgs().uiList("menu.cat-edit.icon.lore", "icon", String.valueOf(s.getString("icon"))))
                .build(), e -> {
            ItemStack cursor = e.getCursor();
            if (cursor == null || cursor.getType().isAir() || !cursor.getType().isItem()) {
                plugin.messages().send(viewer, "msg.category-icon-hint");
                return;
            }
            plugin.getConfig().set(path("icon"), cursor.getType().name());
            plugin.saveConfig();
            new CategoryEditMenu(plugin, viewer, id).open();
        });

        set(12, button(Material.NAME_TAG, "menu.cat-edit.rename", "name", plugin.reports().categoryName(id)),
                e -> plugin.input().request(viewer, "msg.input-category-name", text -> {
                    String name = Text.clean(text, 40);
                    if (!name.isEmpty()) {
                        plugin.getConfig().set(path("name"), name);
                        plugin.saveConfig();
                    }
                    if (viewer.isOnline()) {
                        new CategoryEditMenu(plugin, viewer, id).open();
                    }
                }));

        flag(14, "enabled", enabled, "menu.cat-edit.enabled");
        flag(16, "cheat", cheat, "menu.cat-edit.cheat");
        flag(20, "require-reason", needReason, "menu.cat-edit.require-reason");

        if (cheat) {
            List<String> types = s.getStringList("types");
            set(22, button(Material.WRITABLE_BOOK, "menu.cat-edit.types",
                            "types", types.isEmpty() ? "—" : String.join(", ", types)),
                    e -> plugin.input().request(viewer, "msg.input-category-types", text -> {
                        List<String> list = new ArrayList<>();
                        for (String part : text.split(",")) {
                            String t = Text.clean(part, 24);
                            if (!t.isEmpty() && list.size() < MAX_TYPES) {
                                list.add(t);
                            }
                        }
                        plugin.getConfig().set(path("types"), list);
                        plugin.saveConfig();
                        if (viewer.isOnline()) {
                            new CategoryEditMenu(plugin, viewer, id).open();
                        }
                    }));
        }

        set(24, button(Material.TNT, "menu.cat-edit.delete"), e -> {
            if (!e.isShiftClick()) {
                plugin.messages().send(viewer, "msg.category-delete-hint");
                return;
            }
            if (plugin.reports().categoryIds(false).size() <= 1) {
                plugin.messages().send(viewer, "msg.category-last");
                return;
            }
            plugin.getConfig().set("categories." + id, null);
            plugin.saveConfig();
            plugin.messages().send(viewer, "msg.category-deleted", "id", id);
            new CategoryEditorMenu(plugin, viewer).open();
        });

        addBack(27, () -> new CategoryEditorMenu(plugin, viewer).open());
        addClose(31);
    }

    private void flag(int slot, String key, boolean on, String menuKey) {
        List<String> lore = new ArrayList<>(msgs().uiList(menuKey + ".lore"));
        lore.add(" ");
        lore.add(msgs().ui(on ? "menu.settings.state-on" : "menu.settings.state-off"));
        lore.add(msgs().ui("menu.settings.click-toggle"));
        set(slot, new ItemBuilder(on ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(msgs().ui(menuKey + ".name"))
                .lore(lore)
                .glow(on)
                .build(), e -> {
            plugin.getConfig().set(path(key), !on);
            plugin.saveConfig();
            new CategoryEditMenu(plugin, viewer, id).open();
        });
    }
}
