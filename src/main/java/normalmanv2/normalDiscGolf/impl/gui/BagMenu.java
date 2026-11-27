package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightResolver;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.listener.RoundPlayerListener;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import java.util.Map;

public final class BagMenu extends InventoryGUI {
    private boolean confirmingUpgrade;
    private Map<Integer, DiscModel> shown = Map.of();
    private int page;
    private int lastPage;

    public static void open(Player player) {
        var gui = NDGManager.getInstance().getGuiManager();
        if (DiscFlightResolver.findActiveRound(player) instanceof RoundImpl round) gui.openGUI(new RoundBag(round), player);
        else gui.openGUI(new BagMenu(), player);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, "Your Disc Bag");
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) refresh(player);
    }

    @Override public void refreshForPack(Player player) { refresh(player); }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        var bag = ShopActions.bag(player);
        if (!confirmingUpgrade && slot >= 0 && slot < bag.packed().size()) {
            DiscModel disc = bag.packed().get(slot);
            if (event.getClick() == ClickType.LEFT) ShopActions.unpack(player, disc);
            else if (event.getClick() == ClickType.RIGHT) ShopActions.moveToFront(player, disc);
            else return;
        } else if (event.getClick() == ClickType.LEFT) {
            if (slot == 45) {
                if (confirmingUpgrade) confirmingUpgrade = false;
                else ShopItems.navigate(player, getInventory(), () -> DiscShop.open(player));
            } else if (slot == 53 && !confirmingUpgrade) {
                ShopItems.navigate(player, getInventory(), () -> DecalShop.open(player));
            } else if (slot == 51 && !confirmingUpgrade && bag.upgradeCost() > 0) {
                confirmingUpgrade = true;
            } else if (slot == 31 && confirmingUpgrade) {
                ShopActions.upgrade(player);
                confirmingUpgrade = false;
            } else if (!confirmingUpgrade) {
                if (slot == CatalogPages.PREVIOUS && page > 0) page--;
                else if (slot == CatalogPages.NEXT && page < lastPage) page++;
                else if (shown.containsKey(slot)) ShopActions.pack(player, shown.get(slot));
            }
        } else return;
        refresh(player);
    }

    private void refresh(Player player) {
        var bag = ShopActions.bag(player);
        var menu = getInventory();
        menu.clear();
        menu.setItem(49, ShopItems.icon(Material.GOLD_NUGGET, ChatColor.GOLD + "" + bag.credits() + " credits"));
        if (confirmingUpgrade) {
            menu.setItem(22, ShopItems.icon(Material.BUNDLE, ChatColor.AQUA + "Bag Upgrade",
                    ChatColor.GRAY + "Capacity: " + bag.capacity() + " → " + (bag.capacity() + 2)));
            menu.setItem(31, ShopItems.icon(Material.EMERALD, ChatColor.GREEN + "Confirm upgrade",
                    ChatColor.GOLD + "Spend " + bag.upgradeCost() + " credits"));
            menu.setItem(45, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Back to bag"));
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (i < bag.packed().size()) {
                menu.setItem(i, ShopItems.disc(player, bag.packed().get(i),
                        ChatColor.AQUA + "Bag slot " + (i + 1), ChatColor.GRAY + "Left-click to remove · Right-click to move first"));
            } else {
                menu.setItem(i, ShopItems.icon(i < bag.capacity() ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE,
                        ChatColor.GRAY + (i < bag.capacity() ? "Empty slot" : "Upgrade slot")));
            }
        }
        var custom = CatalogPages.custom(bag::owns);
        lastPage = CatalogPages.lastPage(custom);
        page = Math.min(page, lastPage);
        shown = CatalogPages.entries(page, custom, bag::owns);
        shown.forEach((slot, model) -> menu.setItem(slot, ShopItems.disc(player, model,
                bag.packed().contains(model) ? ChatColor.AQUA + "In your bag" : ChatColor.GREEN + "Click to pack")));
        CatalogPages.controls(menu, page, lastPage);
        menu.setItem(45, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Disc Shop"));
        menu.setItem(51, ShopItems.icon(Material.BUNDLE, ChatColor.AQUA + "Capacity: " + bag.packed().size() + "/" + bag.capacity(),
                ChatColor.GRAY + (bag.upgradeCost() > 0 ? "Click to upgrade for " + bag.upgradeCost() + " credits" : "Fully upgraded")));
        menu.setItem(53, ShopItems.icon(Material.FIREWORK_STAR, ChatColor.LIGHT_PURPLE + "Score Decals"));
    }

    private static final class RoundBag extends InventoryGUI {
        private final RoundImpl round;

        private RoundBag(RoundImpl round) { this.round = round; }

        @Override protected Inventory createInventory() {
            return Bukkit.createInventory(null, 27, "Round Disc Bag");
        }

        @Override public void onOpen(InventoryOpenEvent event) {
            if (event.getPlayer() instanceof Player player) refreshForPack(player);
        }

        @Override public void refreshForPack(Player player) {
            var play = round.play();
            var discs = play.packedDiscs(player.getUniqueId());
            DiscModel locked = play.lockedDisc(player.getUniqueId());
            DiscModel drawn = play.rouletteDisc(player.getUniqueId());
            if (play.usesOneDisc()) {
                getInventory().setItem(4, ShopItems.icon(Material.BUNDLE,
                        locked == null ? ChatColor.GOLD + "Choose one disc" : ChatColor.GREEN + "Locked: " + locked.displayName(),
                        ChatColor.GRAY + "Your choice lasts for this round."));
            } else if (play.usesRoulette()) {
                getInventory().setItem(4, ShopItems.icon(Material.AMETHYST_SHARD,
                        drawn == null ? ChatColor.LIGHT_PURPLE + "Waiting for your next draw"
                                : ChatColor.LIGHT_PURPLE + "Drawn: " + drawn.displayName(),
                        ChatColor.GRAY + "A disc is drawn from this bag for each throw."));
            }
            for (int i = 0; i < discs.size(); i++) {
                DiscModel model = discs.get(i);
                String hint = play.usesRoulette()
                        ? (model.equals(drawn) ? ChatColor.GREEN + "Assigned this throw" : ChatColor.GRAY + "Eligible for future draws")
                        : !play.usesOneDisc() ? ChatColor.AQUA + "Click to select"
                            : locked == null ? ChatColor.GOLD + "Click to lock this disc for the round"
                            : locked.equals(model) ? ChatColor.GREEN + "Locked disc" : ChatColor.GRAY + "Unavailable this round";
                getInventory().setItem(9 + i, ShopItems.disc(player, model, hint));
            }
        }

        @Override public void onClick(InventoryClickEvent event) {
            if (event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player)) return;
            var discs = round.play().packedDiscs(player.getUniqueId());
            int index = event.getRawSlot() - 9;
            if (index >= 0 && index < discs.size()) {
                DiscModel model = discs.get(index);
                ShopItems.navigate(player, getInventory(), () -> {
                    if (DiscFlightResolver.findActiveRound(player) != round) return;
                    if (RoundPlayerListener.selectDisc(player, model)) player.closeInventory();
                });
            }
        }
    }
}
