package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.library.CourseLibraryStore.FeaturedCourse;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public final class FeaturedCoursesMenu extends InventoryGUI {
    public static final String MANAGE_PERMISSION = "ndg.course.featured.manage";
    private static final int FIRST_ENTRY = 9;
    private static final int PAGE_SIZE = 36;
    private final List<FeaturedCourse> courses;
    private int page;

    private FeaturedCoursesMenu(List<FeaturedCourse> courses) {
        super(MenuSkin.LOBBIES, "Featured Courses");
        this.courses = courses;
    }

    public static void open(Player player) {
        var manager = NDGManager.getInstance();
        try {
            var courses = manager.getCourseLibrary().featuredCourses();
            manager.getGuiManager().openGUI(new FeaturedCoursesMenu(courses), player);
        } catch (IOException failure) {
            player.sendMessage(ChatColor.RED + "Could not load Featured Courses. Please try again.");
            NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger()
                    .log(Level.WARNING, "Could not load Featured Courses", failure);
        }
    }

    @Override public void onOpen(InventoryOpenEvent event) { render(event.getPlayer().hasPermission(MANAGE_PERMISSION)); }

    @Override public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        boolean left = event.getClick() == ClickType.LEFT;
        boolean manager = player.hasPermission(MANAGE_PERMISSION);
        int slot = event.getRawSlot();
        if (left && slot == 45) {
            ShopItems.navigate(player, getInventory(), () -> ClubhouseMenu.open(player));
        } else if (left && slot == 48 && page > 0) {
            page--;
            render(manager);
        } else if (left && slot == 50 && page + 1 < pages()) {
            page++;
            render(manager);
        } else if (left && slot == 53 && manager) {
            ShopItems.navigate(player, getInventory(), () -> {
                player.closeInventory();
                player.sendMessage(ChatColor.YELLOW + "Use /ndg featured pin <code> with an active course share code.");
                CourseLibraryMenu.suggest(player, "Enter Share Code", "/ndg featured pin ");
            });
        } else if (slot >= FIRST_ENTRY && slot < FIRST_ENTRY + PAGE_SIZE
                && (left || manager && event.getClick() == ClickType.RIGHT)) {
            int index = page * PAGE_SIZE + slot - FIRST_ENTRY;
            if (index >= courses.size()) return;
            String command = (left ? "ndg featured preview " : "ndg featured unpin ") + courses.get(index).code();
            ShopItems.navigate(player, getInventory(), () -> player.performCommand(command));
        }
    }

    private void render(boolean manager) {
        var menu = getInventory();
        menu.clear();
        menu.setItem(4, ShopItems.icon(Material.WRITTEN_BOOK, ChatColor.GOLD + "Featured Courses",
                ChatColor.GRAY + "Page " + (page + 1) + "/" + pages()));
        if (courses.isEmpty()) menu.setItem(22, ShopItems.icon(Material.BOOK, ChatColor.YELLOW + "No featured courses yet"));
        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(start + PAGE_SIZE, courses.size()); index++) {
            var featured = courses.get(index);
            var lore = CourseDetailsMenu.courseLore(featured.entry());
            lore.add(ChatColor.GREEN + "Left click to preview");
            if (manager) lore.add(ChatColor.YELLOW + "Right click to unpin " + featured.code());
            menu.setItem(FIRST_ENTRY + index - start, ShopItems.icon(Material.FILLED_MAP,
                    ChatColor.AQUA + featured.entry().displayName(), lore.toArray(String[]::new)));
        }
        menu.setItem(45, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Back to Clubhouse"));
        if (page > 0) menu.setItem(48, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Previous Page"));
        if (page + 1 < pages()) menu.setItem(50, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Next Page"));
        if (manager) menu.setItem(53, ShopItems.icon(Material.PAPER, ChatColor.AQUA + "Pin by Share Code"));
    }

    private int pages() { return Math.max(1, (courses.size() + PAGE_SIZE - 1) / PAGE_SIZE); }
}
