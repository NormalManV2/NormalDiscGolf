package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.admin.AdminSettings;
import normalmanv2.normalDiscGolf.impl.admin.SetupWizard;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager.QueueResult;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.io.IOException;

public final class SetupWizardMenu extends InventoryGUI {
    private enum Page { HOME, LENGTH, TERRAIN }

    private final NormalDiscGolfPlugin plugin;
    private final SetupWizard wizard;
    private final String url;
    private SetupWizard.Choices choices;
    private Page page = Page.HOME;

    private SetupWizardMenu(NormalDiscGolfPlugin plugin) throws IOException {
        super(MenuSkin.GUIDE, "NDG Setup Wizard");
        this.plugin = plugin;
        wizard = plugin.setupWizard();
        var saved = plugin.adminSettings().read();
        choices = wizard.choices(saved);
        url = saved.getString("resource-pack.url", "").strip();
    }

    public static void open(Player player) throws IOException {
        if (!player.hasPermission(AdminSettings.PERMISSION)) return;
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        NDGManager.getInstance().getGuiManager().openGUI(new SetupWizardMenu(plugin), player);
    }

    @Override public void onOpen(InventoryOpenEvent event) { render(); }

    @Override public void onClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player)
                || !player.hasPermission(AdminSettings.PERMISSION)) return;
        int slot = event.getRawSlot();
        if (page != Page.HOME) {
            if (slot == 27) page = Page.HOME;
            else if (page == Page.LENGTH && slot >= 9 && slot <= 26) {
                choices = choices.withHoles(slot - 8);
                page = Page.HOME;
            } else if (page == Page.TERRAIN && slot >= 9 && slot <= 9 + CourseTerrain.values().length) {
                choices = choices.withTerrain(slot == 9 ? "random" : CourseTerrain.values()[slot - 10].id());
                page = Page.HOME;
            }
            render();
            return;
        }
        switch (slot) {
            case 10 -> { page = Page.LENGTH; render(); }
            case 12 -> { page = Page.TERRAIN; render(); }
            case 14, 20, 22, 24, 27 -> ShopItems.navigate(player, getInventory(), () -> action(player, slot));
            default -> { }
        }
    }

    private void action(Player player, int slot) {
        if (!player.hasPermission(AdminSettings.PERMISSION)) return;
        try {
            switch (slot) {
                case 14 -> save(player);
                case 20 -> {
                    player.closeInventory();
                    player.sendMessage(ChatColor.GRAY + "Upload NormalDiscGolfPack.zip and enter its HTTPS download URL.");
                    CourseLibraryMenu.suggest(player, "Enter HTTPS Pack URL", "/ndg admin wizard url ");
                }
                case 22 -> {
                    wizard.verify(player, () -> {
                        if (player.getOpenInventory().getTopInventory().equals(getInventory())) refresh();
                    });
                    refresh();
                }
                case 24 -> test(player);
                case 27 -> AdminSettingsMenu.open(player, plugin.adminSettings());
                default -> { }
            }
        } catch (IllegalArgumentException invalid) {
            player.sendMessage(ChatColor.YELLOW + invalid.getMessage());
        } catch (IOException failure) {
            player.sendMessage(ChatColor.RED + "Could not use the saved setup. Check config.yml and the server log.");
            plugin.getLogger().warning("Setup Wizard action failed: " + failure.getMessage());
        }
    }

    private void save(Player player) throws IOException {
        var outcome = wizard.save(choices);
        String message = switch (outcome) {
            case ACTIVE -> "Course defaults saved and active.";
            case REVIEW_PENDING -> "Course defaults saved. Review the other pending changes in Server Settings before applying.";
            case RESTART_REQUIRED -> "Course defaults saved. Restart the server to apply the saved startup settings.";
            case BUSY -> "Course defaults saved. Use Apply Saved Changes when disc golf is idle.";
            case INVALID -> "Course defaults saved. Use Check Config to resolve validation errors.";
        };
        player.sendMessage((outcome == SetupWizard.SaveResult.ACTIVE ? ChatColor.GREEN : ChatColor.YELLOW) + message);
    }

    private void test(Player player) throws IOException {
        QueueResult queued = wizard.testRound(player, choices);
        if (queued == QueueResult.SUCCESS) {
            player.closeInventory();
            player.sendMessage(ChatColor.GREEN + "Your private one-hole setup test is queued.");
        } else {
            String message = switch (queued) {
                case PLAYER_IN_ACTIVE_ROUND -> "Finish your active or preparing round before starting a test.";
                case PLAYER_ALREADY_QUEUED -> "Leave your current queue before starting a test.";
                default -> "Could not queue the test round. Rejoin and try again.";
            };
            player.sendMessage(ChatColor.YELLOW + message);
        }
    }

    private void refresh() {
        render();
        applyTextures();
    }

    private void render() {
        var menu = getInventory();
        menu.clear();
        menu.setItem(27, ShopItems.icon(Material.ARROW, ChatColor.YELLOW
                + (page == Page.HOME ? "Server Settings" : "Back to Setup")));
        if (page == Page.LENGTH) {
            menu.setItem(4, ShopItems.icon(Material.WRITTEN_BOOK, ChatColor.GOLD + "Choose Default Course Length"));
            for (int holes = 1; holes <= 18; holes++) {
                var icon = ShopItems.icon(Material.PAPER, (holes == choices.holes() ? ChatColor.GOLD : ChatColor.AQUA)
                        + Integer.toString(holes) + (holes == 1 ? " Hole" : " Holes"));
                icon.setAmount(holes);
                menu.setItem(holes + 8, icon);
            }
        } else if (page == Page.TERRAIN) {
            menu.setItem(4, ShopItems.icon(Material.MAP, ChatColor.GOLD + "Choose Default Terrain"));
            menu.setItem(9, terrainIcon("random", "Random"));
            for (var terrain : CourseTerrain.values())
                menu.setItem(10 + terrain.ordinal(), terrainIcon(terrain.id(), terrain.label()));
        } else {
            menu.setItem(4, ShopItems.icon(Material.COMPASS, ChatColor.GOLD + "First-run Setup Wizard",
                    ChatColor.GRAY + (choices.profile().isEmpty() ? "Server default course" : "Default preset • " + choices.profile())));
            menu.setItem(10, ShopItems.icon(Material.WRITTEN_BOOK, ChatColor.AQUA + "Default Course Length",
                    ChatColor.GRAY + Integer.toString(choices.holes()) + (choices.holes() == 1 ? " hole" : " holes")));
            menu.setItem(12, ShopItems.icon(Material.MAP, ChatColor.AQUA + "Default Terrain", ChatColor.GRAY + terrainLabel()));
            menu.setItem(14, ShopItems.icon(Material.EMERALD, ChatColor.GREEN + "Save Course Defaults"));
            menu.setItem(20, ShopItems.icon(Material.PAPER, ChatColor.AQUA + "Set HTTPS Pack URL",
                    ChatColor.GRAY + "Upload NormalDiscGolfPack.zip to your host"));
            menu.setItem(22, ShopItems.icon(Material.SPYGLASS, ChatColor.AQUA + "Verify HTTPS Pack",
                    ChatColor.GRAY + wizard.packStatus(url)));
            menu.setItem(24, ShopItems.icon(Material.TARGET, ChatColor.GREEN + "Launch Test Round",
                    ChatColor.GRAY + "One private hole on " + terrainLabel(), ChatColor.GRAY + "Practice only"));
        }
    }

    private String terrainLabel() {
        var terrain = CourseTerrain.parse(choices.terrain());
        return terrain == null ? "Random" : terrain.label();
    }

    private org.bukkit.inventory.ItemStack terrainIcon(String id, String title) {
        return ShopItems.icon(Material.MAP, (id.equals(choices.terrain()) ? ChatColor.GOLD : ChatColor.AQUA) + title);
    }
}
