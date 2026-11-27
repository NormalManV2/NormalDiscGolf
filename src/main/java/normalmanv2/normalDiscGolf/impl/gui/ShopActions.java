package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightResolver;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.disc.DiscStock;
import normalmanv2.normalDiscGolf.impl.player.DiscBag;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Instant;

final class ShopActions {
    private ShopActions() {}

    static boolean betweenRounds(Player player) {
        if (DiscFlightResolver.findActiveRound(player) == null) return true;
        player.sendMessage(ChatColor.RED + "You can change your collection and bag between rounds.");
        return false;
    }

    static DiscBag bag(Player player) {
        return NDGManager.getInstance().getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId()).getDiscBag();
    }

    static DiscStock stock() {
        int hours = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class)
                .getConfig().getInt("disc-shop.rotation-hours", 24);
        return DiscStock.at(Instant.now(), hours, NDGManager.getInstance().getDiscCatalog().models());
    }

    static boolean buy(Player player, DiscModel model) {
        if (!betweenRounds(player)) return false;
        DiscBag bag = bag(player);
        if (bag.owns(model)) return warn(player, "You already own " + model.displayName() + ".");
        if (!stock().offers().contains(model)) return warn(player, model.displayName() + " is out of stock. Check the next rotation.");
        if (bag.credits() < model.price()) return warn(player, "You need " + (model.price() - bag.credits()) + " more credits.");
        return save(player, bag.unlock(model), "Unlocked " + model.displayName() + " for " + model.price() + " credits.");
    }

    static boolean pack(Player player, DiscModel model) {
        if (!betweenRounds(player)) return false;
        DiscBag bag = bag(player);
        if (!bag.owns(model)) return warn(player, "Unlock " + model.displayName() + " first.");
        if (bag.packed().contains(model)) return warn(player, model.displayName() + " is already in your bag.");
        if (bag.packed().size() == bag.capacity()) return warn(player, "Your bag is full. Remove a disc or upgrade your bag.");
        return save(player, bag.pack(model), "Packed " + model.displayName() + " for your next round.");
    }

    static boolean unpack(Player player, DiscModel model) {
        if (!betweenRounds(player)) return false;
        DiscBag bag = bag(player);
        if (!bag.packed().contains(model)) return warn(player, model.displayName() + " is not in your bag.");
        if (bag.packed().size() == 1) return warn(player, "Keep at least one disc in your bag.");
        return save(player, bag.unpack(model), "Removed " + model.displayName() + " from your round bag.");
    }

    static void moveToFront(Player player, DiscModel model) {
        if (!betweenRounds(player)) return;
        DiscBag bag = bag(player);
        if (bag.packed().contains(model) && bag.packed().getFirst() != model)
            save(player, bag.moveToFront(model), model.displayName() + " is now first in your bag.");
    }

    static void upgrade(Player player) {
        if (!betweenRounds(player)) return;
        DiscBag bag = bag(player);
        int cost = bag.upgradeCost();
        if (cost == 0) { warn(player, "Your bag is fully upgraded."); return; }
        if (bag.credits() < cost) { warn(player, "You need " + (cost - bag.credits()) + " more credits to upgrade."); return; }
        save(player, bag.upgrade(), "Bag upgraded to " + (bag.capacity() + 2) + " slots for " + cost + " credits.");
    }

    private static boolean save(Player player, DiscBag next, String message) {
        var manager = NDGManager.getInstance().getPlayerDataManager();
        var data = manager.getOrCreatePlayerData(player.getUniqueId());
        var previous = data.getDiscBag();
        data.setDiscBag(next);
        if (!manager.savePlayerData(player.getUniqueId())) {
            data.setDiscBag(previous);
            return warn(player, "The shop could not save your change. Please try again.");
        }
        player.sendMessage(ChatColor.GREEN + message);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .45f, 1.3f);
        return true;
    }

    private static boolean warn(Player player, String message) {
        player.sendMessage(ChatColor.YELLOW + message);
        return false;
    }
}
