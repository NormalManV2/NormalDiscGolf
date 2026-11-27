package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.player.score.RoundSummary;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

public final class RoundSummaryMenu extends InventoryGUI {
    private static final Material[] METRIC_ICONS = {Material.COMPASS, Material.LIGHT_BLUE_DYE,
            Material.MOSS_BLOCK, Material.TARGET, Material.REDSTONE};
    private final RoundSummary summary;

    private RoundSummaryMenu(RoundSummary summary) {
        super(MenuSkin.SUMMARY);
        this.summary = summary;
    }

    public static void open(Player player, RoundSummary summary) {
        NDGManager.getInstance().getGuiManager().openGUI(new RoundSummaryMenu(summary), player);
    }

    @Override public void onOpen(InventoryOpenEvent event) {
        getInventory().setItem(4, ShopItems.icon(Material.WRITTEN_BOOK, ChatColor.GOLD + "Round Result",
                ChatColor.GRAY + summary.roundLabel(), ChatColor.WHITE + summary.scoreLine()));
        var metrics = summary.metrics();
        for (int i = 0; i < metrics.size(); i++) {
            var metric = metrics.get(i);
            getInventory().setItem(11 + i, ShopItems.icon(METRIC_ICONS[i], ChatColor.AQUA + metric.name(),
                    ChatColor.WHITE + metric.value()));
        }
        getInventory().setItem(27, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Close"));
        getInventory().setItem(31, ShopItems.icon(Material.PAPER, ChatColor.AQUA + "Show in Chat"));
        getInventory().setItem(35, ShopItems.icon(Material.EMERALD, ChatColor.GREEN + "Play Another Round"));
    }

    @Override public void onClick(InventoryClickEvent event) {
        if (!event.isLeftClick() || !(event.getWhoClicked() instanceof Player player)) return;
        switch (event.getRawSlot()) {
            case 27 -> ShopItems.navigate(player, getInventory(), player::closeInventory);
            case 31 -> ShopItems.navigate(player, getInventory(), () -> {
                player.closeInventory();
                summary.sendTo(player);
            });
            case 35 -> ShopItems.navigate(player, getInventory(), () -> player.performCommand("ndg menu"));
            default -> { }
        }
    }
}
