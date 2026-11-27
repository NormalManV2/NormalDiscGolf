package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

final class CatalogPages {
    private static final int[] SLOTS = {10, 11, 12, 13, 19, 20, 21, 22, 28, 29, 30, 31};
    static final int PREVIOUS = 46;
    static final int NEXT = 52;

    private CatalogPages() {}

    static List<DiscModel> custom(Predicate<DiscModel> filter) {
        return NDGManager.getInstance().getDiscCatalog().models().stream()
                .skip(DiscModel.builtIns().size()).filter(filter).toList();
    }

    static int lastPage(List<DiscModel> custom) {
        return (custom.size() + SLOTS.length - 1) / SLOTS.length;
    }

    static Map<Integer, DiscModel> entries(int page, List<DiscModel> custom, Predicate<DiscModel> filter) {
        Map<Integer, DiscModel> entries = new LinkedHashMap<>();
        List<DiscModel> discs = page == 0 ? DiscModel.builtIns() : custom;
        int start = page == 0 ? 0 : (page - 1) * SLOTS.length;
        for (int i = 0; i < SLOTS.length && start + i < discs.size(); i++) {
            DiscModel disc = discs.get(start + i);
            if (filter.test(disc)) entries.put(SLOTS[i], disc);
        }
        return entries;
    }

    static void controls(Inventory menu, int page, int last) {
        if (last == 0) return;
        if (page > 0) menu.setItem(PREVIOUS, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Previous page"));
        if (page < last) menu.setItem(NEXT, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Next page"));
        menu.setItem(50, ShopItems.icon(Material.BOOK, ChatColor.AQUA + (page == 0 ? "Built-in Discs" : "Custom Discs"),
                ChatColor.GRAY + "Page " + (page + 1) + " / " + (last + 1)));
    }
}
