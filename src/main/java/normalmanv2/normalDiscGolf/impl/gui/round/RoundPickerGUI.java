package normalmanv2.normalDiscGolf.impl.gui.round;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.button.Button;
import normalmanv2.normalDiscGolf.common.item.ItemBuilder;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.gui.InventoryGUI;
import normalmanv2.normalDiscGolf.impl.gui.PlayGuide;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import normalmanv2.normalDiscGolf.impl.round.RoundFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.function.Consumer;

public final class RoundPickerGUI extends InventoryGUI {
    public RoundPickerGUI() {
        super(MenuSkin.ROUNDS);
        addButton(4, new Button(new ItemBuilder(Material.SPRUCE_SIGN).setDisplayName("&6Clubhouse")
                .setLore("&7Pro shop, Daily leaderboard, and practice.").build()) {
            @Override public void onClick(InventoryClickEvent event) {
                afterClick(event, normalmanv2.normalDiscGolf.impl.gui.ClubhouseMenu::open);
            }
        });
        addButton(8, new Button(new ItemBuilder(Material.WRITTEN_BOOK).setDisplayName("&bDisc Golf Guide")
                .setLore("&7Learn the controls and find practice.").build()) {
            @Override public void onClick(InventoryClickEvent event) { afterClick(event, PlayGuide::open); }
        });
        addMode(11, Material.EMERALD, RoundFormat.FFA, "Play a free-for-all round.");
        addMode(12, Material.SLIME_BALL, RoundFormat.ONE_DISC, "Choose one disc for the whole round.");
        addMode(13, Material.GOLD_INGOT, RoundFormat.MATCH, "Play head-to-head. Win the most holes.");
        addMode(14, Material.AMETHYST_SHARD, RoundFormat.DISC_ROULETTE, "Draw a random disc for each throw.");
        addMode(15, Material.DIAMOND, RoundFormat.DOUBLES, "Play in two-player teams.");
        addMode(16, Material.LIME_DYE, RoundFormat.BEST_SHOT, "Both teammates throw, then choose a lie.");
        addMode(17, Material.ORANGE_DYE, RoundFormat.WORST_SHOT, "Both teammates throw. Play the worse result.");
        addMode(21, Material.CLOCK, RoundFormat.DAILY, "Same course for everyone today.", "&eRight-click for leaderboard");
        addMode(22, Material.BLAZE_POWDER, RoundFormat.ELIMINATION, "The highest score goes out after each hole.");
        addMode(23, Material.TARGET, RoundFormat.ACE_RACE, "One throw per short hole. Most aces wins.");
        addMode(24, Material.ENDER_EYE, RoundFormat.CLOSEST_TO_PIN, "One throw per hole. Land closest to the basket.");
        addMode(25, Material.FIREWORK_ROCKET, RoundFormat.ARCADE, "Stroke play with limited-use special discs.");
        addMode(26, Material.GOLDEN_HELMET, RoundFormat.WEEKLY, "See the next event and registration commands.");
    }

    private void addMode(int slot, Material icon, RoundFormat format, String description, String... extra) {
        boolean scheduled = format == RoundFormat.WEEKLY;
        String[] lore = new String[extra.length + (scheduled ? 1 : 2)];
        lore[0] = "&7" + description;
        if (!scheduled) lore[1] = "&eClick to browse lobbies";
        System.arraycopy(extra, 0, lore, lore.length - extra.length, extra.length);
        addButton(slot, new Button(new ItemBuilder(icon).setDisplayName("&a" + format.label()).setLore(lore).build()) {
            @Override public void onClick(InventoryClickEvent event) {
                boolean leaderboard = format == RoundFormat.DAILY && event.isRightClick();
                afterClick(event, player -> {
                    if (scheduled || leaderboard) {
                        player.closeInventory();
                        player.performCommand(scheduled ? "ndg tournament" : "ndg daily");
                    } else NDGManager.getInstance().getGuiManager().openGUI(new CourseLobbiesGUI(format), player);
                });
            }
        });
    }

    static void afterClick(InventoryClickEvent event, Consumer<Player> action) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory source = event.getView().getTopInventory();
        Bukkit.getScheduler().runTask(NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class), () -> {
            if (player.isOnline() && source.equals(player.getOpenInventory().getTopInventory())) action.accept(player);
        });
    }

}
