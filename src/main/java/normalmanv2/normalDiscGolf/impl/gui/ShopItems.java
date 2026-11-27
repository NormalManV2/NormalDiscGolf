package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.resourcepack.DiscItems;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class ShopItems {
    private ShopItems() {}

    static ItemStack icon(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack disc(Player player, DiscModel model, String... details) {
        ItemStack item = DiscItems.discItem(model, player, false);
        var meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + model.flightStats());
        lore.addAll(List.of(details));
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    static void navigate(Player player, Inventory current, Runnable action) {
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(current)) action.run();
        });
    }
}
