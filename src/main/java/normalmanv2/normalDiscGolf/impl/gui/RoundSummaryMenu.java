package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.player.score.RoundSummary;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.io.IOException;
import java.util.logging.Level;

public final class RoundSummaryMenu extends InventoryGUI {
    private static final Material[] METRIC_ICONS = {Material.COMPASS, Material.LIGHT_BLUE_DYE,
            Material.MOSS_BLOCK, Material.TARGET, Material.REDSTONE};
    private final RoundSummary summary;
    private boolean courseSaved;

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
        if (summary.generatedCourse() != null) {
            try {
                courseSaved = NDGManager.getInstance().getCourseLibrary()
                        .find(event.getPlayer().getUniqueId(), summary.generatedCourse().id()).isPresent();
            } catch (IOException failure) {
                NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger()
                        .log(Level.WARNING, "Could not check saved course status", failure);
            }
            renderSaveButton();
        }
    }

    @Override public void onClick(InventoryClickEvent event) {
        if (!event.isLeftClick() || !(event.getWhoClicked() instanceof Player player)) return;
        switch (event.getRawSlot()) {
            case 27 -> ShopItems.navigate(player, getInventory(), player::closeInventory);
            case 31 -> ShopItems.navigate(player, getInventory(), () -> {
                player.closeInventory();
                summary.sendTo(player);
            });
            case 33 -> {
                if (summary.generatedCourse() != null) ShopItems.navigate(player, getInventory(), () -> saveCourse(player));
            }
            case 35 -> ShopItems.navigate(player, getInventory(), () -> player.performCommand("ndg menu"));
            default -> { }
        }
    }

    private void renderSaveButton() {
        getInventory().setItem(33, courseSaved
                ? ShopItems.icon(Material.MAP, ChatColor.GREEN + "Course Saved", ChatColor.GRAY + "Open your Course Library")
                : ShopItems.icon(Material.FILLED_MAP, ChatColor.AQUA + "Save Course",
                        ChatColor.GRAY + summary.generatedCourse().label(), ChatColor.GRAY + "Replay this layout from the clubhouse"));
    }

    private void saveCourse(Player player) {
        if (courseSaved) {
            CourseLibraryMenu.open(player);
            return;
        }
        try {
            boolean added = NDGManager.getInstance().getCourseLibrary().save(player.getUniqueId(), summary.generatedCourse());
            courseSaved = true;
            renderSaveButton();
            applyTextures();
            player.sendMessage(ChatColor.GREEN + (added ? "Course saved to your Course Library." : "This course is already in your Course Library."));
        } catch (IOException failure) {
            player.sendMessage(ChatColor.RED + "Could not save the course. Please try again.");
            NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger()
                    .log(Level.WARNING, "Could not save course " + summary.generatedCourse().id(), failure);
        }
    }
}
