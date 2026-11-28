package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.library.CourseLibraryEntry;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.ArrayList;
import java.util.List;

public final class CourseDetailsMenu extends InventoryGUI {
    private enum Source {
        OWNED("Manage Saved Course"), SHARED("Shared Course Preview"), FEATURED("Featured Course Preview");

        private final String title;

        Source(String title) { this.title = title; }
    }

    private final CourseLibraryEntry entry;
    private final Source source;
    private final String code;
    private final boolean saved;

    private CourseDetailsMenu(CourseLibraryEntry entry, Source source, String code, boolean saved) {
        super(MenuSkin.GUIDE, source.title);
        this.entry = entry;
        this.source = source;
        this.code = code;
        this.saved = saved;
    }

    public static void openOwned(Player player, CourseLibraryEntry entry, String code) {
        NDGManager.getInstance().getGuiManager().openGUI(new CourseDetailsMenu(entry, Source.OWNED, code, true), player);
    }

    public static void openShared(Player player, CourseLibraryEntry entry, String code, boolean saved, boolean featured) {
        var source = featured ? Source.FEATURED : Source.SHARED;
        NDGManager.getInstance().getGuiManager().openGUI(new CourseDetailsMenu(entry, source, code, saved), player);
    }

    @Override public void onOpen(InventoryOpenEvent event) {
        var menu = getInventory();
        menu.setItem(4, ShopItems.icon(Material.FILLED_MAP, ChatColor.GOLD + entry.displayName(),
                courseLore(entry).toArray(String[]::new)));
        menu.setItem(27, ShopItems.icon(Material.ARROW,
                ChatColor.YELLOW + (source == Source.FEATURED ? "Back to Featured Courses" : "Back to Course Library")));
        menu.setItem(29, ShopItems.icon(Material.WRITTEN_BOOK, ChatColor.AQUA + "Course Records",
                ChatColor.GRAY + "View stroke-play standings and your best"));
        if (entry.course().replayFormat() != RoundFormat.FFA)
            menu.setItem(31, ShopItems.icon(Material.TARGET, ChatColor.GREEN + "Play for Record",
                    ChatColor.GRAY + "Play stroke rules on this layout to set a best and record a ghost"));
        menu.setItem(33, ShopItems.icon(Material.EMERALD, ChatColor.GREEN + "Queue Course",
                ChatColor.GRAY + "Play solo or with your party"));
        if (source != Source.OWNED) {
            menu.setItem(22, saved
                    ? ShopItems.icon(Material.BOOK, ChatColor.GREEN + "Saved to Your Library")
                    : ShopItems.icon(Material.BOOK, ChatColor.AQUA + "Save a Copy",
                            ChatColor.GRAY + "Add this layout to your personal library"));
            return;
        }
        menu.setItem(20, ShopItems.icon(Material.NAME_TAG, ChatColor.AQUA + "Rename Course",
                ChatColor.GRAY + "Enter a name in chat"));
        menu.setItem(26, ShopItems.icon(Material.FIREWORK_STAR,
                entry.favorite() ? ChatColor.YELLOW + "Remove from Favorites" : ChatColor.GOLD + "Add to Favorites"));
        menu.setItem(22, code == null
                ? ShopItems.icon(Material.PAPER, ChatColor.AQUA + "Create Share Code",
                        ChatColor.GRAY + "Give friends access to this layout")
                : ShopItems.icon(Material.PAPER, ChatColor.AQUA + "Show Share Code",
                        ChatColor.WHITE + code, ChatColor.GRAY + "Copy the code from chat"));
        if (code != null) menu.setItem(24, ShopItems.icon(Material.BARRIER, ChatColor.RED + "Revoke Share Code",
                ChatColor.GRAY + "Disable this code for future lookups"));
    }

    @Override public void onClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        var id = entry.course().id();
        if (slot == 27) {
            ShopItems.navigate(player, getInventory(), () -> {
                if (source == Source.FEATURED) FeaturedCoursesMenu.open(player);
                else CourseLibraryMenu.open(player);
            });
        } else if (slot == 29) {
            run(player, switch (source) {
                case OWNED -> "library board " + id;
                case SHARED -> "library board-code " + code;
                case FEATURED -> "featured board " + code;
            });
        } else if (slot == 31 && entry.course().replayFormat() != RoundFormat.FFA) {
            run(player, switch (source) {
                case OWNED -> "library play-record " + id;
                case SHARED -> "library record-code " + code;
                case FEATURED -> "featured play-record " + code;
            });
        } else if (slot == 33) {
            run(player, switch (source) {
                case OWNED -> "library play " + id;
                case SHARED -> "library play-code " + code;
                case FEATURED -> "featured play " + code;
            });
        } else if (source != Source.OWNED) {
            if (slot == 22 && !saved) run(player, (source == Source.FEATURED ? "featured save " : "library save-code ") + code);
        } else if (slot == 20) {
            ShopItems.navigate(player, getInventory(), () -> {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Choose a course name of up to " + CourseLibraryEntry.MAX_NAME_LENGTH + " characters.");
                CourseLibraryMenu.suggest(player, "Enter Course Name", "/ndg library rename " + id + " ");
            });
        } else if (slot == 22) {
            run(player, "library share " + id);
        } else if (slot == 24 && code != null) {
            run(player, "library revoke " + id);
        } else if (slot == 26) {
            run(player, "library " + (entry.favorite() ? "unfavorite " : "favorite ") + id);
        }
    }

    private void run(Player player, String action) {
        ShopItems.navigate(player, getInventory(), () -> player.performCommand("ndg " + action));
    }

    static List<String> courseLore(CourseLibraryEntry entry) {
        var course = entry.course();
        var lore = new ArrayList<String>();
        if (entry.name() != null) lore.add(ChatColor.GRAY + course.label());
        lore.add(ChatColor.GRAY + "Seed " + course.seed() + " • Par " + course.par()
                + (course.difficulty() == 0 ? "" : " • Difficulty " + course.difficulty() + "/100"));
        if (course.preset() != null) lore.add(ChatColor.GRAY + "Preset " + course.preset());
        lore.add(ChatColor.WHITE + course.replayFormat().label() + " • Unrated");
        return lore;
    }
}
