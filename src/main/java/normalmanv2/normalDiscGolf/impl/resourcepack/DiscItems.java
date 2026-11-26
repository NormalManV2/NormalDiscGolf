package normalmanv2.normalDiscGolf.impl.resourcepack;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class DiscItems {
    private DiscItems() {}
    public static ItemStack discItem(DiscType type, String name) {
        ItemStack item = new ItemStack(type == DiscType.PUTTER ? Material.SLIME_BALL : type == DiscType.MID_RANGE ? Material.HEART_OF_THE_SEA : Material.FIRE_CHARGE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a" + name);


        item.setItemMeta(meta);
        return item;
    }
    public static ItemStack discItem(DiscType type, String name, org.bukkit.entity.Player player, boolean display) {
        ItemStack item = discItem(type, name);
        try {
            var pack = normalmanv2.normalDiscGolf.impl.NDGManager.getInstance().getPacked();
            boolean available = display ? player.getWorld().getPlayers().stream().allMatch(pack::loaded) : pack.loaded(player);
            if (available) {
                var meta = item.getItemMeta();
                meta.setItemModel(new org.bukkit.NamespacedKey("ndg", type == DiscType.PUTTER ? "putter" : type == DiscType.MID_RANGE ? "midrange" : "driver"));
                item.setItemMeta(meta);
            }
        } catch (java.io.IOException ignored) { /* The labeled vanilla item remains usable. */ }
        return item;
    }
}
