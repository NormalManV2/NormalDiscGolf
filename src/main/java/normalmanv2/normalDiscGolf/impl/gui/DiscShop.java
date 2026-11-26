package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightResolver;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.player.DiscBag;
import normalmanv2.normalDiscGolf.impl.resourcepack.DiscItems;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class DiscShop extends InventoryGUI {
    private static final DiscModel[] MODELS = DiscModel.values();
    private static final int[] DISC_SLOTS = {1, 4, 7, 10, 13, 16, 19, 22, 25};

    public static void open(Player player) {
        if (available(player)) NDGManager.getInstance().getGuiManager().openGUI(new DiscShop(), player);
    }

    private static boolean available(Player player) {
        if (DiscFlightResolver.findActiveRound(player) == null) return true;
        player.sendMessage(ChatColor.RED + "Use the disc shop between rounds.");
        return false;
    }

    public static void change(Player player, DiscModel model, boolean purchase) {
        if (!available(player)) return;
        var manager = NDGManager.getInstance().getPlayerDataManager();
        var data = manager.getOrCreatePlayerData(player.getUniqueId());
        DiscBag previous = data.getDiscBag();
        DiscBag next;
        if (purchase) {
            if (previous.owns(model)) {
                player.sendMessage(ChatColor.YELLOW + "You already own " + model.displayName() + ".");
                return;
            }
            if (previous.credits() < model.price()) {
                player.sendMessage(ChatColor.RED + "You need " + (model.price() - previous.credits())
                        + " more credits to unlock " + model.displayName() + ".");
                return;
            }
            next = previous.unlock(model);
        } else {
            if (!previous.owns(model)) {
                player.sendMessage(ChatColor.RED + "Unlock " + model.displayName() + " before equipping it.");
                return;
            }
            if (previous.equipped(model.type()) == model) {
                player.sendMessage(ChatColor.YELLOW + model.displayName() + " is already equipped.");
                return;
            }
            next = previous.equip(model);
        }

        data.setDiscBag(next);
        if (!manager.savePlayerData(player.getUniqueId())) {
            data.setDiscBag(previous);
            player.sendMessage(ChatColor.RED + "The shop could not save your change. Please try again.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + (purchase
                ? "Unlocked " + model.displayName() + " for " + model.price() + " credits. Balance: " + next.credits() + "."
                : "Equipped " + model.displayName() + " for your next round."));
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 36, "Disc Shop");
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) refresh(player);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (event.getClick() != ClickType.LEFT || !(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() == 34) {
            var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
            Bukkit.getScheduler().runTask(plugin, () -> DecalShop.open(player));
            return;
        }
        for (int i = 0; i < DISC_SLOTS.length; i++) {
            if (DISC_SLOTS[i] != event.getRawSlot()) continue;
            DiscModel model = MODELS[i];
            change(player, model, !bag(player).owns(model));
            refresh(player);
            break;
        }
    }

    private void refresh(Player player) {
        DiscBag bag = bag(player);
        for (int i = 0; i < MODELS.length; i++) {
            DiscModel model = MODELS[i];
            ItemStack item = DiscItems.discItem(model.type(), model.displayName(), player, false);
            var meta = item.getItemMeta();
            String action = bag.equipped(model.type()) == model ? ChatColor.AQUA + "Equipped"
                    : bag.owns(model) ? ChatColor.GREEN + "Click to equip"
                    : ChatColor.GOLD + "Click to unlock for " + model.price() + " credits";
            meta.setLore(List.of(ChatColor.GRAY + model.flightStats(), action));
            item.setItemMeta(meta);
            getInventory().setItem(DISC_SLOTS[i], item);
        }

        ItemStack balance = new ItemStack(Material.GOLD_NUGGET);
        var meta = balance.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Balance: " + bag.credits() + " credits");
        meta.setLore(List.of(ChatColor.GRAY + "Complete rounds to earn credits for baskets scored."));
        balance.setItemMeta(meta);
        getInventory().setItem(31, balance);
        ItemStack decals = new ItemStack(Material.FIREWORK_STAR);
        var decalsMeta = decals.getItemMeta();
        decalsMeta.setDisplayName(ChatColor.LIGHT_PURPLE + "Score Decals");
        decalsMeta.setLore(List.of(ChatColor.GRAY + "Preview, unlock, and equip basket celebrations."));
        decals.setItemMeta(decalsMeta);
        getInventory().setItem(34, decals);
    }

    private DiscBag bag(Player player) {
        return NDGManager.getInstance().getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId()).getDiscBag();
    }
}
