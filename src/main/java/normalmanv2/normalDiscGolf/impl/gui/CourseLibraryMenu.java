package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.library.CourseLibraryEntry;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

public final class CourseLibraryMenu extends InventoryGUI {
    private static final int FIRST_ENTRY = 9;
    private static final int PAGE_SIZE = 36;
    private final List<CourseLibraryEntry> courses;
    private final List<CourseLibraryEntry> favorites;
    private boolean favoritesOnly;
    private int page;

    private CourseLibraryMenu(List<CourseLibraryEntry> courses) {
        super(MenuSkin.LOBBIES, "Course Library");
        this.courses = courses.stream().sorted(Comparator.comparing(CourseLibraryEntry::favorite).reversed()
                .thenComparing(CourseLibraryEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(entry -> entry.course().id())).toList();
        this.favorites = this.courses.stream().filter(CourseLibraryEntry::favorite).toList();
    }

    public static void promptForCode(Player player) {
        player.closeInventory();
        player.sendMessage(ChatColor.YELLOW + "Enter a friend's code with /ndg library code <code>.");
        suggest(player, "Enter Share Code", "/ndg library code ");
    }

    static void suggest(Player player, String label, String command) {
        var prompt = new TextComponent("[" + label + "]");
        prompt.setColor(net.md_5.bungee.api.ChatColor.AQUA);
        prompt.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command));
        player.spigot().sendMessage(prompt);
    }

    public static void open(Player player) {
        var manager = NDGManager.getInstance();
        try {
            var courses = manager.getCourseLibrary().list(player.getUniqueId());
            manager.getGuiManager().openGUI(new CourseLibraryMenu(courses), player);
        } catch (IOException failure) {
            player.sendMessage(ChatColor.RED + "Could not load your Course Library. Please try again.");
            NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger()
                    .log(Level.WARNING, "Could not load Course Library for " + player.getUniqueId(), failure);
        }
    }

    @Override public void onOpen(InventoryOpenEvent event) { render(); }

    @Override public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        boolean left = event.getClick() == ClickType.LEFT;
        boolean right = event.getClick() == ClickType.RIGHT;
        if (!left && !right) return;
        int slot = event.getRawSlot();
        if (left && slot == 45) {
            ShopItems.navigate(player, getInventory(), () -> ClubhouseMenu.open(player));
        } else if (left && slot == 48 && page > 0) {
            page--;
            render();
        } else if (left && slot == 50 && page + 1 < pages()) {
            page++;
            render();
        } else if (left && slot == 51) {
            favoritesOnly = !favoritesOnly;
            page = 0;
            render();
        } else if (left && slot == 53) {
            ShopItems.navigate(player, getInventory(), () -> promptForCode(player));
        } else if (slot >= FIRST_ENTRY && slot < FIRST_ENTRY + PAGE_SIZE) {
            int index = page * PAGE_SIZE + slot - FIRST_ENTRY;
            var visible = visibleCourses();
            if (index >= visible.size()) return;
            var id = visible.get(index).course().id();
            String action = right ? "manage" : "play";
            ShopItems.navigate(player, getInventory(), () -> player.performCommand("ndg library " + action + " " + id));
        }
    }

    private void render() {
        var menu = getInventory();
        menu.clear();
        menu.setItem(4, ShopItems.icon(Material.MAP, ChatColor.GOLD + "Course Library",
                ChatColor.GRAY + (favoritesOnly ? "Favorites" : "Your saved layouts") + " • Page " + (page + 1) + "/" + pages()));
        var visible = visibleCourses();
        if (visible.isEmpty()) {
            menu.setItem(22, favoritesOnly
                    ? ShopItems.icon(Material.BOOK, ChatColor.YELLOW + "No favorite courses",
                            ChatColor.GRAY + "Add a favorite from a saved course's details.")
                    : ShopItems.icon(Material.BOOK, ChatColor.YELLOW + "No saved courses",
                            ChatColor.GRAY + "Save a generated course from a round summary."));
        }
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, visible.size()); index++) {
            var entry = visible.get(index);
            var lore = CourseDetailsMenu.courseLore(entry);
            if (entry.favorite()) lore.add(0, ChatColor.GOLD + "Favorite");
            lore.add(ChatColor.GREEN + "Left click to queue solo or with your party");
            lore.add(ChatColor.AQUA + "Right click for course details and records");
            menu.setItem(FIRST_ENTRY + index - start,
                    ShopItems.icon(Material.FILLED_MAP, ChatColor.AQUA + entry.displayName(), lore.toArray(String[]::new)));
        }
        menu.setItem(45, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Back to Clubhouse"));
        if (page > 0) menu.setItem(48, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Previous Page"));
        if (page + 1 < pages()) menu.setItem(50, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Next Page"));
        menu.setItem(51, ShopItems.icon(Material.FIREWORK_STAR, ChatColor.GOLD + (favoritesOnly ? "Show All Courses" : "Show Favorites")));
        menu.setItem(53, ShopItems.icon(Material.PAPER, ChatColor.AQUA + "Enter Share Code",
                ChatColor.GRAY + "Preview a course shared by a friend"));
    }

    private List<CourseLibraryEntry> visibleCourses() { return favoritesOnly ? favorites : courses; }

    private int pages() { return Math.max(1, Bounds.ceilDiv(visibleCourses().size(), PAGE_SIZE)); }
}
