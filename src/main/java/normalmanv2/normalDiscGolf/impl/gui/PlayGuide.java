package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

public final class PlayGuide extends InventoryGUI {
    public static void open(Player player) {
        NDGManager.getInstance().getGuiManager().openGUI(new PlayGuide(), player);
    }

    private PlayGuide() { super(MenuSkin.GUIDE); }

    @Override public void onOpen(InventoryOpenEvent event) {
        info(4, Material.WRITTEN_BOOK, "Playing Disc Golf",
                "Finish each hole by sinking your disc in the basket.",
                "Your next shot starts where the disc lands.");
        info(10, Material.SLIME_BALL, "Aim and Release",
                "Select a disc and right-click to open the meter.",
                "Click at your chosen power. With timing enabled,",
                "click again when the timing bar turns green.",
                "Use your drop key to cancel aim during a round.");
        info(12, Material.COMPASS, "Shape Your Shot",
                "Swap hands to switch backhand and forehand.",
                "Sneak + swap hands to cycle release angles.",
                "/ndg shot shows stances, wind, timing, and preview.");
        info(14, Material.SPYGLASS, "Find Your Line",
                "Follow the flyover from the tee to the basket.",
                "Sneak to skip a flyover or exit the disc camera.",
                "Use the compass or /ndg lie to return to your disc.");
        info(16, Material.BOOK, "Scoring",
                "/ndg score shows your card during a round.",
                "/ndg summary shows your most recent completed round.",
                "Click to see your current scorecard.");
        info(20, Material.TARGET, "Putting Green", "Try any shop disc, even while queued.");
        info(22, Material.BUNDLE, "Your Bag", "Choose which owned discs you bring into a round.");
        info(24, Material.CLOCK, "Daily Course", "See today's course, standings, and personal best.");
        info(27, Material.ARROW, "Round Picker", "Browse multiplayer modes and open lobbies.");
        info(35, Material.SPRUCE_SIGN, "Clubhouse", "Find the Pro Shop, practice, and career progress.");
    }

    @Override public void onClick(InventoryClickEvent event) {
        if (!event.isLeftClick() || !(event.getWhoClicked() instanceof Player player)) return;
        String command = switch (event.getRawSlot()) {
            case 16 -> "score";
            case 20 -> "practice";
            case 22 -> "bag";
            case 24 -> "daily";
            case 27 -> "menu";
            case 35 -> "clubhouse";
            default -> null;
        };
        if (command == null) return;
        ShopItems.navigate(player, getInventory(), () -> {
            if (command.equals("daily")) player.closeInventory();
            player.performCommand("ndg " + command);
        });
    }

    private void info(int slot, Material material, String title, String... details) {
        getInventory().setItem(slot, ShopItems.icon(material, ChatColor.AQUA + title, details));
    }
}
