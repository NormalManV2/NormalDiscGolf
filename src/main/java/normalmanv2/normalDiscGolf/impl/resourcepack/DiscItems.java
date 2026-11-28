package normalmanv2.normalDiscGolf.impl.resourcepack;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.disc.ArcadeDisc;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.util.BukkitCompatibility;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class DiscItems {
    private DiscItems() {}
    public static ItemStack discItem(DiscType type, String name) {
        return namedItem(discMaterial(type), name);
    }

    static Material discMaterial(DiscType type) {
        return type == DiscType.PUTTER ? Material.SLIME_BALL : type == DiscType.MID_RANGE ? Material.HEART_OF_THE_SEA : Material.FIRE_CHARGE;
    }

    private static ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§a" + name);
        item.setItemMeta(meta);
        return item;
    }
    public static ItemStack discItem(DiscType type, String name, org.bukkit.entity.Player player, boolean display) {
        DiscModel model = NDGManager.getInstance().getDiscCatalog().models().stream()
                .filter(disc -> disc.type() == type && disc.displayName().equals(name))
                .findFirst().orElse(DiscModel.starter(type));
        return applyModel(discItem(type, name), model, player, display);
    }

    public static ItemStack discItem(DiscModel model, org.bukkit.entity.Player player, boolean display) {
        return applyModel(discItem(model.type(), model.displayName()), model, player, display);
    }

    public static ItemStack arcadeItem(ArcadeDisc disc, org.bukkit.entity.Player player, boolean display) {
        return applyModel(namedItem(disc.icon(), disc.model().displayName()), disc.model(), player, display);
    }

    private static ItemStack applyModel(ItemStack item, DiscModel model, org.bukkit.entity.Player player, boolean display) {
        try {
            var pack = NDGManager.getInstance().getPacked();
            boolean available = display ? player.getWorld().getPlayers().stream().allMatch(pack::loaded) : pack.loaded(player);
            if (available) {
                var meta = item.getItemMeta();
                if (pack.legacyModels() != null) meta.setCustomModelData(pack.legacyModels().modelData(model));
                else BukkitCompatibility.itemModel(meta, new org.bukkit.NamespacedKey("ndg", model.id()));
                item.setItemMeta(meta);
            }
        } catch (java.io.IOException ignored) { /* The labeled vanilla item remains usable. */ }
        return item;
    }
}
