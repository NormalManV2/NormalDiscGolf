package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.Map;

public final class PracticeMenu extends InventoryGUI {
    private final int distance;
    private Map<Integer, DiscModel> shown = Map.of();
    private int page;
    private int lastPage;

    private PracticeMenu(int distance) {
        super(MenuSkin.PRACTICE);
        this.distance = distance;
    }

    public static void open(Player player, int distance) {
        NDGManager.getInstance().getGuiManager().openGUI(new PracticeMenu(distance), player);
    }

    @Override public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) refresh(player);
    }

    @Override public void refreshForPack(Player player) { refresh(player); }

    @Override public void onClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot == 45) {
            ShopItems.navigate(player, getInventory(), () -> ClubhouseMenu.open(player));
            return;
        }
        DiscModel disc = shown.get(slot);
        if (disc != null) {
            ShopItems.navigate(player, getInventory(), () -> NDGManager.getInstance().getClubhouse().tryDisc(player, disc, distance));
        } else if (slot == CatalogPages.PREVIOUS && page > 0) {
            page--;
            refresh(player);
        } else if (slot == CatalogPages.NEXT && page < lastPage) {
            page++;
            refresh(player);
        }
    }

    private void refresh(Player player) {
        var menu = getInventory();
        menu.clear();
        var custom = CatalogPages.custom(model -> true);
        lastPage = CatalogPages.lastPage(custom);
        shown = CatalogPages.entries(page, custom, model -> true);
        menu.setItem(4, ShopItems.icon(Material.TARGET, ChatColor.GREEN + "Try a Disc",
                ChatColor.GRAY + "Free practice · 4, 8, and 12 block tees",
                ChatColor.GRAY + "Your queue spot stays active"));
        shown.forEach((slot, model) -> menu.setItem(slot, ShopItems.disc(player, model, ChatColor.YELLOW + "Click to take a practice throw")));
        CatalogPages.controls(menu, page, lastPage);
        menu.setItem(45, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Back to Clubhouse"));
    }
}
