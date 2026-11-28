package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.admin.AdminSettings;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

public final class AdminBackupsMenu extends InventoryGUI {
    private AdminBackupsMenu() { super(MenuSkin.GUIDE, "NDG Backups"); }

    public static void open(Player player) {
        if (player.hasPermission(AdminSettings.PERMISSION))
            NDGManager.getInstance().getGuiManager().openGUI(new AdminBackupsMenu(), player);
    }

    @Override public void onOpen(InventoryOpenEvent event) {
        var menu = getInventory();
        menu.setItem(4, ShopItems.icon(Material.CHEST, ChatColor.GOLD + "Backups and Upgrade Checks"));
        menu.setItem(11, ShopItems.icon(Material.BUNDLE, ChatColor.GREEN + "Create Backup",
                ChatColor.GRAY + "Save player and course data to a ZIP"));
        menu.setItem(13, ShopItems.icon(Material.WRITTEN_BOOK, ChatColor.AQUA + "Browse Backups",
                ChatColor.GRAY + "Choose an archive for a restore preview or upgrade comparison"));
        menu.setItem(15, ShopItems.icon(Material.SPYGLASS, ChatColor.AQUA + "Check Saved Layouts",
                ChatColor.GRAY + "Regenerate the current Course Library layouts and report compatibility"));
        menu.setItem(22, ShopItems.icon(Material.CLOCK, ChatColor.AQUA + "Automatic Backup Schedule",
                ChatColor.GRAY + "Check the interval, retention limit, and next backup"));
        menu.setItem(27, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Back to Server Settings"));
    }

    @Override public void onClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player)
                || !player.hasPermission(AdminSettings.PERMISSION)) return;
        String action = switch (event.getRawSlot()) {
            case 11 -> "backup create";
            case 13 -> "backup list";
            case 15 -> "upgrade check";
            case 22 -> "backup status";
            case 27 -> "";
            default -> null;
        };
        if (action == null) return;
        ShopItems.navigate(player, getInventory(), () -> {
            player.closeInventory();
            player.performCommand(action.isEmpty() ? "ndg admin" : "ndg admin " + action);
        });
    }
}
