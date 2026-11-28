package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.competition.StrokeStandings;
import normalmanv2.normalDiscGolf.impl.course.library.CourseLibraryEntry;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.List;
import java.util.UUID;

public final class CourseRecordsMenu extends InventoryGUI {
    private static final int FIRST_ENTRY = 9;
    private static final int PAGE_SIZE = 36;
    private final CourseLibraryEntry entry;
    private final List<StrokeStandings.Standing> standings;
    private final String backCommand;
    private final String textCommand;
    private int page;

    private CourseRecordsMenu(CourseLibraryEntry entry, List<StrokeStandings.Standing> standings,
                              String backCommand, String textCommand) {
        super(MenuSkin.SCORECARD, "Course Records");
        this.entry = entry;
        this.standings = standings;
        this.backCommand = backCommand;
        this.textCommand = textCommand;
    }

    public static void open(Player player, CourseLibraryEntry entry, List<StrokeStandings.Standing> standings,
                            String backCommand, String textCommand) {
        NDGManager.getInstance().getGuiManager()
                .openGUI(new CourseRecordsMenu(entry, standings, backCommand, textCommand), player);
    }

    @Override public void onOpen(InventoryOpenEvent event) { render(event.getPlayer().getUniqueId()); }

    @Override public void onClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player)) return;
        switch (event.getRawSlot()) {
            case 45 -> ShopItems.navigate(player, getInventory(), () -> player.performCommand(backCommand));
            case 48 -> {
                if (page > 0) {
                    page--;
                    render(player.getUniqueId());
                }
            }
            case 50 -> {
                if (page + 1 < pages()) {
                    page++;
                    render(player.getUniqueId());
                }
            }
            case 53 -> ShopItems.navigate(player, getInventory(), () -> player.performCommand(textCommand));
            default -> { }
        }
    }

    private void render(UUID viewer) {
        var menu = getInventory();
        menu.clear();
        menu.setItem(4, ShopItems.icon(Material.WRITTEN_BOOK, ChatColor.GOLD + entry.displayName(),
                ChatColor.GRAY + "Stroke play • Page " + (page + 1) + "/" + pages()));
        if (standings.isEmpty()) menu.setItem(22, ShopItems.icon(Material.BOOK, ChatColor.YELLOW + "No completed cards yet",
                ChatColor.GRAY + "Hole out on every hole in a saved-layout stroke round."));
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, standings.size()); index++) {
            var row = standings.get(index);
            boolean own = viewer.equals(row.entry().player());
            menu.setItem(FIRST_ENTRY + index - start, ShopItems.icon(own ? Material.GOLD_NUGGET : Material.PAPER,
                    (own ? ChatColor.GOLD : ChatColor.AQUA) + Integer.toString(row.place()) + ". " + row.entry().name(),
                    ChatColor.WHITE + row.entry().score()));
        }
        var best = standings.stream().filter(row -> viewer.equals(row.entry().player())).findFirst().orElse(null);
        menu.setItem(49, ShopItems.icon(Material.FIREWORK_STAR, ChatColor.GOLD + "Your Course Best",
                ChatColor.WHITE + (best == null ? "No completed card yet" : best.entry().score() + " • Place " + best.place())));
        menu.setItem(45, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Back to Course"));
        if (page > 0) menu.setItem(48, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Previous Page"));
        if (page + 1 < pages()) menu.setItem(50, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Next Page"));
        menu.setItem(53, ShopItems.icon(Material.PAPER, ChatColor.AQUA + "Show in Chat"));
    }

    private int pages() { return Math.max(1, Bounds.ceilDiv(standings.size(), PAGE_SIZE)); }
}
