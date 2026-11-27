package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public final class DiscShop extends InventoryGUI {
    private static final DateTimeFormatter RESTOCK_TIME =
            DateTimeFormatter.ofPattern("EEE HH:mm 'UTC'", java.util.Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private DiscModel selected;
    private final Map<Integer, DiscModel> shown = new HashMap<>();
    private int page;
    private int lastPage;

    public static void open(Player player) {
        if (ShopActions.betweenRounds(player)) NDGManager.getInstance().getGuiManager().openGUI(new DiscShop(), player);
    }

    public static void change(Player player, DiscModel model, boolean purchase) {
        if (purchase) ShopActions.buy(player, model);
        else ShopActions.pack(player, model);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, "Parkland Pro Shop");
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) refresh(player);
    }

    @Override public void refreshForPack(Player player) { refresh(player); }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot == 48) {
            ShopItems.navigate(player, getInventory(), () -> BagMenu.open(player));
            return;
        }
        if (slot == 47) {
            player.performCommand("ndg pack retry");
            refresh(player);
            return;
        }
        if (slot == 53) {
            ShopItems.navigate(player, getInventory(), () -> DecalShop.open(player));
            return;
        }
        if (slot == 44) {
            ShopItems.navigate(player, getInventory(), () -> ClubhouseMenu.open(player));
            return;
        }
        if (selected != null) {
            if (slot == 45) selected = null;
            else if (slot == 31) {
                var bag = ShopActions.bag(player);
                if (!bag.owns(selected)) ShopActions.buy(player, selected);
                else if (!bag.packed().contains(selected)) ShopActions.pack(player, selected);
            } else return;
            refresh(player);
            return;
        }
        if (slot == 4) {
            refresh(player);
            return;
        }
        if (slot == CatalogPages.PREVIOUS && page > 0) {
            page--;
            refresh(player);
        } else if (slot == CatalogPages.NEXT && page < lastPage) {
            page++;
            refresh(player);
        } else if (shown.containsKey(slot)) {
            selected = shown.get(slot);
            refresh(player);
        }
    }

    private void refresh(Player player) {
        Inventory menu = getInventory();
        menu.clear();
        shown.clear();
        var bag = ShopActions.bag(player);
        var stock = ShopActions.stock();
        menu.setItem(4, ShopItems.icon(Material.CLOCK, ChatColor.GOLD + "Featured Rack",
                ChatColor.GRAY + (stock.restockAt() == null ? "All discs are available."
                        : "Next restock: " + RESTOCK_TIME.format(stock.restockAt())),
                ChatColor.GRAY + "Click to refresh stock"));
        menu.setItem(48, ShopItems.icon(Material.BUNDLE, ChatColor.AQUA + "Your Bag",
                ChatColor.GRAY + "" + bag.packed().size() + "/" + bag.capacity() + " discs packed"));
        String modelStatus = "Vanilla icons · click to retry";
        try {
            var pack = NDGManager.getInstance().getPacked();
            if (pack.loaded(player)) modelStatus = "Custom disc models active";
            else if (pack.pending(player)) modelStatus = "Waiting for Minecraft to load the pack";
        } catch (java.io.IOException ignored) { /* Keep the fallback status visible. */ }
        menu.setItem(47, ShopItems.icon(Material.PAINTING, ChatColor.AQUA + "Disc Models", ChatColor.GRAY + modelStatus));
        menu.setItem(49, ShopItems.icon(Material.GOLD_NUGGET, ChatColor.GOLD + "" + bag.credits() + " credits",
                ChatColor.GRAY + "Earn credits by sinking baskets and finishing rounds."));
        menu.setItem(53, ShopItems.icon(Material.FIREWORK_STAR, ChatColor.LIGHT_PURPLE + "Score Decals"));
        menu.setItem(44, ShopItems.icon(Material.WRITABLE_BOOK, ChatColor.AQUA + "Clubhouse",
                ChatColor.GRAY + "Daily challenge, records and medals"));
        if (selected == null) {
            var custom = CatalogPages.custom(model -> true);
            lastPage = CatalogPages.lastPage(custom);
            page = Math.min(page, lastPage);
            CatalogPages.controls(menu, page, lastPage);
            if (page == 0) {
                menu.setItem(9, ShopItems.icon(Material.LIME_DYE, ChatColor.GREEN + "Putters"));
                menu.setItem(18, ShopItems.icon(Material.LIGHT_BLUE_DYE, ChatColor.AQUA + "Midranges"));
                menu.setItem(27, ShopItems.icon(Material.ORANGE_DYE, ChatColor.GOLD + "Drivers"));
            }
            shown.putAll(CatalogPages.entries(page, custom, model -> true));
            if (stock.restockAt() != null) {
                int featuredSlot = 5;
                for (DiscModel featured : stock.offers().stream()
                        .sorted(java.util.Comparator.comparing(DiscModel::type).reversed()).toList()) {
                    shown.put(featuredSlot++, featured);
                }
            }
            shown.forEach((slot, model) -> {
                String status;
                if (bag.packed().contains(model)) status = ChatColor.AQUA + "Packed";
                else if (bag.owns(model)) status = ChatColor.GREEN + "Owned";
                else if (stock.offers().contains(model)) status = ChatColor.GOLD + "In stock · " + model.price() + " credits";
                else status = ChatColor.DARK_GRAY + "Out of stock";
                menu.setItem(slot, ShopItems.disc(player, model, status, ChatColor.GRAY + "Click to inspect"));
            });
            return;
        }
        menu.setItem(45, ShopItems.icon(Material.ARROW, ChatColor.YELLOW + "Back to the rack"));
        menu.setItem(22, ShopItems.disc(player, selected,
                bag.owns(selected) ? ChatColor.GREEN + "In your collection" : ChatColor.GOLD + "Price: " + selected.price() + " credits"));
        if (bag.packed().contains(selected)) {
            menu.setItem(31, ShopItems.icon(Material.LIME_DYE, ChatColor.GREEN + "Already packed"));
        } else if (bag.owns(selected)) {
            menu.setItem(31, ShopItems.icon(Material.BUNDLE, ChatColor.AQUA + "Pack for next round",
                    ChatColor.GRAY + "" + bag.packed().size() + "/" + bag.capacity() + " slots used"));
        } else if (!stock.offers().contains(selected)) {
            menu.setItem(31, ShopItems.icon(Material.GRAY_DYE, ChatColor.GRAY + "Out of stock"));
        } else if (bag.credits() >= selected.price()) {
            menu.setItem(31, ShopItems.icon(Material.EMERALD, ChatColor.GREEN + "Confirm purchase",
                    ChatColor.GOLD + "Spend " + selected.price() + " credits",
                    ChatColor.GRAY + "Balance after purchase: " + (bag.credits() - selected.price())));
        } else {
            menu.setItem(31, ShopItems.icon(Material.GRAY_DYE, ChatColor.GRAY + "Insufficient credits",
                    ChatColor.GOLD + "You need " + (selected.price() - bag.credits()) + " more"));
        }
    }
}
