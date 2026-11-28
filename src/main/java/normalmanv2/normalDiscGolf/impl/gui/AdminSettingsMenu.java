package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.util.Bounds;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.admin.AdminSettings;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public final class AdminSettingsMenu extends InventoryGUI {
    private enum Category {
        PLAY("Round Rules", Material.TARGET, Set.of()),
        COURSES("Courses", Material.MAP, Set.of("course", "daily-course", "ace-race", "closest-to-pin")),
        EVENTS("Events and Competitions", Material.CLOCK, Set.of("course-events", "ranked-seasons", "weekly-tournament")),
        ECONOMY("Shop and Career", Material.EMERALD, Set.of("disc-shop", "career")),
        PRESENTATION("Pack and Presentation", Material.SPYGLASS, Set.of("resource-pack", "clubhouse", "preview", "spectating"));

        final String title;
        final Material icon;
        final Set<String> roots;

        Category(String title, Material icon, Set<String> roots) {
            this.title = title;
            this.icon = icon;
            this.roots = roots;
        }

        static Category forKey(String key) {
            String root = key.split("\\.", 2)[0];
            for (var category : values()) if (category.roots.contains(root)) return category;
            return PLAY;
        }
    }

    private static final int PAGE_SIZE = 36;
    private final AdminSettings settings;
    private final YamlConfiguration saved;
    private Category category;
    private List<String> keys = List.of();
    private int page;

    private AdminSettingsMenu(AdminSettings settings, YamlConfiguration saved) {
        super(MenuSkin.LOBBIES, "NormalDiscGolf Settings");
        this.settings = settings;
        this.saved = saved;
    }

    public static void open(Player player, AdminSettings settings) throws IOException {
        if (player.hasPermission(AdminSettings.PERMISSION))
            NDGManager.getInstance().getGuiManager().openGUI(new AdminSettingsMenu(settings, settings.read()), player);
    }

    @Override public void onOpen(InventoryOpenEvent event) { render(); }

    @Override public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission(AdminSettings.PERMISSION)) return;
        int slot = event.getRawSlot();
        boolean left = event.getClick() == ClickType.LEFT;
        boolean right = event.getClick() == ClickType.RIGHT;
        if (left && slot == 31 && category == null) run(player, "wizard");
        else if (left && slot == 33 && category == null) run(player, "backup");
        else if (left && slot == 47) run(player, "check");
        else if (left && slot == 49) run(player, "apply");
        else if (left && slot == 51) run(player, "status");
        else if (left && slot == 45 && category != null) {
            category = null;
            render();
        } else if (category == null) {
            if (left && slot >= 19 && slot <= 27 && (slot - 19) % 2 == 0) {
                category = Category.values()[(slot - 19) / 2];
                keys = settings.schema().keys(saved).stream().filter(key -> Category.forKey(key) == category).toList();
                page = 0;
                render();
            }
        } else if (left && slot == 46 && page > 0) {
            page--;
            render();
        } else if (left && slot == 53 && page + 1 < pages()) {
            page++;
            render();
        } else if (slot >= 9 && slot < 45 && (left || right)) {
            int index = page * PAGE_SIZE + slot - 9;
            if (index >= keys.size()) return;
            String key = keys.get(index);
            ShopItems.navigate(player, getInventory(), () -> {
                if (!player.hasPermission(AdminSettings.PERMISSION)) return;
                player.closeInventory();
                if (right) player.performCommand("ndg admin reset " + key);
                else {
                    player.sendMessage(ChatColor.GRAY + "Saved value • " + AdminSettings.display(key, saved.get(key)));
                    CourseLibraryMenu.suggest(player, "Edit " + key, "/ndg admin set " + key + " ");
                }
            });
        }
    }

    private void run(Player player, String action) {
        ShopItems.navigate(player, getInventory(), () -> {
            player.closeInventory();
            player.performCommand("ndg admin " + action);
        });
    }

    private void render() {
        var menu = getInventory();
        menu.clear();
        int pending = settings.pending(saved).size();
        if (category == null) {
            menu.setItem(4, ShopItems.icon(Material.WRITTEN_BOOK, ChatColor.GOLD + "Server Settings",
                    ChatColor.GRAY + "Saved changes pending • " + pending));
            int slot = 19;
            for (var group : Category.values()) {
                menu.setItem(slot, ShopItems.icon(group.icon, ChatColor.AQUA + group.title));
                slot += 2;
            }
            menu.setItem(31, ShopItems.icon(Material.COMPASS, ChatColor.GOLD + "First-run Setup Wizard",
                    ChatColor.GRAY + "Course defaults, HTTPS pack verification, and a test round"));
            menu.setItem(33, ShopItems.icon(Material.CHEST, ChatColor.GOLD + "Backups and Upgrade Checks",
                    ChatColor.GRAY + "Back up NDG data, preview a restore, and check saved layouts"));
        } else {
            menu.setItem(4, ShopItems.icon(category.icon, ChatColor.GOLD + category.title,
                    ChatColor.GRAY + "Page " + (page + 1) + "/" + pages()));
            int start = page * PAGE_SIZE;
            for (int index = start; index < Math.min(start + PAGE_SIZE, keys.size()); index++) {
                String key = keys.get(index);
                menu.setItem(9 + index - start, ShopItems.icon(Material.PAPER, ChatColor.AQUA + key,
                        ChatColor.GRAY + "Saved • " + AdminSettings.display(key, saved.get(key)),
                        ChatColor.GRAY + (settings.schema().restartOnly(key) ? "Applies after restart" : "Can apply when idle"),
                        ChatColor.GREEN + "Left click to edit • Right click to reset"));
            }
            menu.setItem(45, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Back to Settings"));
            if (page > 0) menu.setItem(46, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Previous Page"));
            if (page + 1 < pages()) menu.setItem(53, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Next Page"));
        }
        menu.setItem(47, ShopItems.icon(Material.BOOK, ChatColor.AQUA + "Check Config"));
        menu.setItem(49, ShopItems.icon(Material.EMERALD, ChatColor.GREEN + "Apply Saved Changes"));
        menu.setItem(51, ShopItems.icon(Material.COMPASS, ChatColor.AQUA + "Setup Status"));
    }

    private int pages() { return Math.max(1, Bounds.ceilDiv(keys.size(), PAGE_SIZE)); }
}
