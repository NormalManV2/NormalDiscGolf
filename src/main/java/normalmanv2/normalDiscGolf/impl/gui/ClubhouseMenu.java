package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.player.clubhouse.ClubhouseMedal;
import normalmanv2.normalDiscGolf.impl.player.clubhouse.ClubhouseProgress;
import normalmanv2.normalDiscGolf.impl.player.clubhouse.DailyChallenge;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

public final class ClubhouseMenu extends InventoryGUI {
    private record RecordPage(String title, Material icon, List<RoundFormat> formats) {}
    private static final List<RecordPage> RECORD_PAGES = List.of(
            new RecordPage("FFA and Doubles Scorecards", Material.BOOK, List.of(RoundFormat.FFA, RoundFormat.DOUBLES)),
            new RecordPage("Best and Worst Shot Scorecards", Material.LIME_DYE, List.of(RoundFormat.BEST_SHOT, RoundFormat.WORST_SHOT)),
            new RecordPage("One Disc and Roulette Scorecards", Material.SLIME_BALL, List.of(RoundFormat.ONE_DISC, RoundFormat.DISC_ROULETTE)));
    private boolean showingRecords;
    private int recordsPage;

    public static void open(Player player) {
        NDGManager.getInstance().getGuiManager().openGUI(new ClubhouseMenu(), player);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, "Parkland Clubhouse");
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) refresh(player);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if ((!showingRecords && slot == 16) || (showingRecords && slot == 45)) {
            showingRecords = !showingRecords;
            refresh(player);
        } else if (showingRecords && slot == 53) {
            recordsPage = (recordsPage + 1) % RECORD_PAGES.size();
            refresh(player);
        } else if (!showingRecords) {
            switch (slot) {
                case 45 -> ShopItems.navigate(player, getInventory(), () -> DiscShop.open(player));
                case 47 -> ShopItems.navigate(player, getInventory(), () -> BagMenu.open(player));
                case 49 -> ShopItems.navigate(player, getInventory(), () -> player.performCommand("ndg menu"));
                case 53 -> ShopItems.navigate(player, getInventory(), () -> DecalShop.open(player));
                case 4 -> refresh(player);
                case 31 -> ShopItems.navigate(player, getInventory(), () -> {
                    player.closeInventory();
                    player.performCommand("ndg career");
                });
                default -> { }
            }
        }
    }

    private void refresh(Player player) {
        Inventory menu = getInventory();
        menu.clear();
        var playerData = NDGManager.getInstance().getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId());
        var progress = playerData.getClubhouse();
        if (showingRecords) {
            showRecords(menu, progress);
            return;
        }
        LocalDate day = LocalDate.now(ZoneOffset.UTC);
        DailyChallenge daily = DailyChallenge.forDay(day);
        int count = progress.dailyProgress(day);
        menu.setItem(4, ShopItems.icon(Material.CLOCK, ChatColor.GOLD + daily.title(),
                ChatColor.GRAY + daily.description(),
                count == daily.target() ? ChatColor.GREEN + "Complete · reward earned"
                        : ChatColor.AQUA + "" + count + " / " + daily.target() + " · " + daily.credits() + " credits",
                ChatColor.GRAY + "Rotates daily at midnight UTC"));
        menu.setItem(10, ShopItems.icon(Material.IRON_BARS, ChatColor.AQUA + "Personal Baskets",
                ChatColor.GRAY + "Baskets: " + progress.baskets(),
                ChatColor.GRAY + "Under par: " + progress.underPar(),
                ChatColor.GRAY + "Aces: " + progress.aces()));
        menu.setItem(12, ShopItems.icon(Material.SPYGLASS, ChatColor.AQUA + "Longest Basket",
                ChatColor.GRAY + (progress.longestBasket() > 0
                        ? String.format(Locale.ROOT, "%.1f blocks", progress.longestBasket()) : "Sink a basket to set a record.")));
        menu.setItem(14, ShopItems.icon(Material.BLAZE_POWDER, ChatColor.GOLD + "Best Basket Streak",
                ChatColor.GRAY + "" + progress.bestStreak() + " consecutive holes"));
        menu.setItem(16, ShopItems.icon(Material.WRITTEN_BOOK, ChatColor.AQUA + "Best Scorecards",
                ChatColor.GRAY + "Click for your round records"));
        var rating = playerData.getRating();
        menu.setItem(31, ShopItems.icon(Material.EXPERIENCE_BOTTLE, ChatColor.GOLD + "Career",
                ChatColor.AQUA + rating.getCareerDivision().label(),
                ChatColor.GRAY + (rating.getRating() == 0 ? "Unrated" : "Rating " + rating.getRating()),
                ChatColor.GRAY + "Click to view skills and rating progress"));

        int slot = 19;
        for (ClubhouseMedal medal : ClubhouseMedal.values()) {
            boolean earned = progress.medals().contains(medal);
            menu.setItem(slot++, ShopItems.icon(earned ? Material.GOLD_INGOT : Material.IRON_NUGGET,
                    (earned ? ChatColor.GOLD : ChatColor.GRAY) + medal.title(), ChatColor.GRAY + medal.description(),
                    earned ? ChatColor.GREEN + "Earned" : ChatColor.YELLOW + "" + medal.credits() + " credits"));
        }
        menu.setItem(45, ShopItems.icon(Material.EMERALD, ChatColor.GREEN + "Pro Shop"));
        menu.setItem(47, ShopItems.icon(Material.BUNDLE, ChatColor.AQUA + "Your Bag"));
        menu.setItem(49, ShopItems.icon(Material.COMPASS, ChatColor.GREEN + "Play a Round",
                ChatColor.GRAY + "Balance: " + playerData.getDiscBag().credits() + " credits"));
        menu.setItem(53, ShopItems.icon(Material.FIREWORK_STAR, ChatColor.LIGHT_PURPLE + "Score Decals"));
    }

    private void showRecords(Inventory menu, ClubhouseProgress progress) {
        RecordPage page = RECORD_PAGES.get(recordsPage);
        menu.setItem(4, ShopItems.icon(Material.WRITTEN_BOOK,
                ChatColor.AQUA + page.title(),
                ChatColor.GRAY + "Hole out on every hole to qualify.",
                ChatColor.GRAY + "Records are separate by format and round length."));
        int slot = 9;
        for (var format : page.formats()) {
            for (int holes = 1; holes <= 18; holes++) {
                Integer score = progress.bestCards().get(format.id() + "-" + holes);
                if (score == null) continue;
                String relative = score == 0 ? "Even par" : (score > 0 ? "+" : "") + score;
                menu.setItem(slot++, ShopItems.icon(Material.PAPER,
                        ChatColor.AQUA + format.label() + " · " + holes
                                + (holes == 1 ? " hole" : " holes"),
                        ChatColor.GRAY + relative));
            }
        }
        menu.setItem(45, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Back to Clubhouse"));
        RecordPage next = RECORD_PAGES.get((recordsPage + 1) % RECORD_PAGES.size());
        menu.setItem(53, ShopItems.icon(next.icon(), ChatColor.YELLOW + next.title()));
    }
}
