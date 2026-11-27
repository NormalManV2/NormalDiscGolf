package normalmanv2.normalDiscGolf.impl.gui;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.cosmetic.ScoreDecal;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightResolver;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.resourcepack.MenuSkin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class DecalShop extends InventoryGUI {
    public static void open(Player player) {
        if (available(player)) NDGManager.getInstance().getGuiManager().openGUI(new DecalShop(), player);
    }

    public static void change(Player player, ScoreDecal decal, boolean purchase) {
        if (!available(player)) return;
        var manager = NDGManager.getInstance().getPlayerDataManager();
        var data = manager.getOrCreatePlayerData(player.getUniqueId());
        var oldLocker = data.getDecalLocker();
        var oldBag = data.getDiscBag();
        if (purchase) {
            if (oldLocker.owns(decal)) {
                player.sendMessage(ChatColor.YELLOW + "You already own " + decal.displayName() + ".");
                return;
            }
            if (oldBag.credits() < decal.price()) {
                player.sendMessage(ChatColor.RED + "You need " + (decal.price() - oldBag.credits())
                        + " more credits to unlock " + decal.displayName() + ".");
                return;
            }
            data.setDecalLocker(oldLocker.unlock(decal));
            data.setDiscBag(oldBag.spend(decal.price()));
        } else {
            if (!oldLocker.owns(decal)) {
                player.sendMessage(ChatColor.RED + "Unlock " + decal.displayName() + " before equipping it.");
                return;
            }
            if (oldLocker.equipped() == decal) {
                player.sendMessage(ChatColor.YELLOW + decal.displayName() + " is already selected.");
                return;
            }
            data.setDecalLocker(oldLocker.equip(decal));
        }

        if (!manager.savePlayerData(player.getUniqueId())) {
            data.setDecalLocker(oldLocker);
            data.setDiscBag(oldBag);
            player.sendMessage(ChatColor.RED + "The shop could not save your change. Please try again.");
            return;
        }
        if (purchase) {
            player.sendMessage(ChatColor.GREEN + "Unlocked " + decal.displayName() + " for " + decal.price()
                    + " credits. Balance: " + data.getDiscBag().credits() + ".");
        } else if (decal == ScoreDecal.NONE) {
            player.sendMessage(ChatColor.GREEN + "Score decal cleared.");
        } else {
            player.sendMessage(ChatColor.GREEN + "Equipped " + decal.displayName() + " as your score decal.");
        }
    }

    public static void preview(Player player, ScoreDecal decal) {
        if (!available(player)) return;
        if (decal == ScoreDecal.NONE) {
            player.sendMessage(ChatColor.YELLOW + "Choose a score decal to preview.");
            return;
        }
        player.closeInventory();
        decal.preview(player);
        player.sendMessage(ChatColor.GRAY + "Previewing " + decal.displayName() + ".");
    }

    private static boolean available(Player player) {
        if (DiscFlightResolver.findActiveRound(player) == null) return true;
        player.sendMessage(ChatColor.RED + "Use the score decal shop between rounds.");
        return false;
    }

    private DecalShop() { super(MenuSkin.DECALS); }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) refresh(profile(player));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot == 18 && event.getClick() == ClickType.LEFT) {
            ShopItems.navigate(player, getInventory(), () -> DiscShop.open(player));
            return;
        }
        if (slot == 26 && event.getClick() == ClickType.LEFT) {
            ShopItems.navigate(player, getInventory(), () -> BagMenu.open(player));
            return;
        }
        if (slot < 10 || slot > 16 || slot % 2 != 0) return;
        ScoreDecal decal = ScoreDecal.values()[(slot - 10) / 2];
        if (event.getClick() == ClickType.RIGHT) {
            ShopItems.navigate(player, getInventory(), () -> preview(player, decal));
        } else if (event.getClick() == ClickType.LEFT) {
            PlayerData data = profile(player);
            change(player, decal, !data.getDecalLocker().owns(decal));
            refresh(data);
        }
    }

    private void refresh(PlayerData data) {
        var locker = data.getDecalLocker();
        ScoreDecal[] decals = ScoreDecal.values();
        for (int i = 0; i < decals.length; i++) {
            ScoreDecal decal = decals[i];
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + decal.description());
            if (locker.equipped() == decal) lore.add(ChatColor.AQUA + "Selected");
            else if (locker.owns(decal)) lore.add(ChatColor.GREEN + "Left-click to select");
            else lore.add(ChatColor.GOLD + "Left-click to unlock for " + decal.price() + " credits");
            if (decal != ScoreDecal.NONE) lore.add(ChatColor.AQUA + "Right-click to preview");
            getInventory().setItem(10 + 2 * i, icon(decal.icon(), ChatColor.GREEN + decal.displayName(), lore));
        }
        getInventory().setItem(18, icon(Material.ARROW, ChatColor.YELLOW + "Back to Disc Shop", List.of()));
        getInventory().setItem(22, icon(Material.GOLD_NUGGET,
                ChatColor.GOLD + "Balance: " + data.getDiscBag().credits() + " credits", List.of()));
        getInventory().setItem(26, icon(Material.BUNDLE, ChatColor.AQUA + "Your Bag", List.of()));
    }

    private static ItemStack icon(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static PlayerData profile(Player player) {
        return NDGManager.getInstance().getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId());
    }

}
