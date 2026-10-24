package com.raporx.util;

import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ItemBuilder {
    private static final int LORE_WIDTH = 40;

    private final ItemStack item;
    private final ItemMeta meta;
    private final List<String> lore = new ArrayList<>();

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material == null ? Material.PAPER : material);
        this.meta = item.getItemMeta();
    }

    /** Config'ten gelen materyal adını güvenle çözer. */
    public static ItemBuilder of(String materialName, Material fallback) {
        Material m = materialName == null ? null : Material.matchMaterial(materialName.trim());
        if (m == null || m.isAir() || !m.isItem()) {
            m = fallback;
        }
        return new ItemBuilder(m);
    }

    public ItemBuilder name(String name) {
        if (meta != null) {
            meta.setDisplayName(name);
        }
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        lore.addAll(lines);
        return this;
    }

    public ItemBuilder lore(String... lines) {
        lore.addAll(Arrays.asList(lines));
        return this;
    }

    public ItemBuilder amount(int amount) {
        item.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow && meta != null) {
            meta.setEnchantmentGlintOverride(true);
        }
        return this;
    }

    public ItemBuilder head(OfflinePlayer player) {
        if (meta instanceof SkullMeta skull) {
            skull.setOwningPlayer(player);
        }
        return this;
    }

    public ItemStack build() {
        if (meta != null) {
            if (!lore.isEmpty()) {
                List<String> wrapped = new ArrayList<>();
                for (String line : lore) {
                    wrapped.addAll(Text.wrap(line, LORE_WIDTH));
                }
                meta.setLore(wrapped);
            }
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }
}
