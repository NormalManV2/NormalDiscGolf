package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightResolver;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.resourcepack.DiscItems;
import normalmanv2.normalDiscGolf.impl.round.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

public final class RoundPlayerListener implements Listener {
    private static NamespacedKey key() { return new NamespacedKey(NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class), "control"); }
    private final Map<UUID, String> techniques = new HashMap<>();
    private RoundImpl round(Player player) { var round = DiscFlightResolver.findActiveRound(player); return round instanceof RoundImpl impl ? impl : null; }
    private boolean course(World world) { return NDGManager.getInstance().getRoundHandler().isCourseWorld(world); }
    private static ItemStack control(ItemStack item, String action) {
        var meta=item.getItemMeta(); meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, action);
        meta.setLore(List.of("§7Right-click to use", "§7Swap hands to change throw technique")); item.setItemMeta(meta); return item;
    }
    private static ItemStack tool(Material material, String title, String action) {
        ItemStack item=new ItemStack(material); var meta=item.getItemMeta(); meta.setDisplayName(title); item.setItemMeta(meta); return control(item,action);
    }
    public static void giveKit(Player player) {
        player.getInventory().clear();
        var bag = NDGManager.getInstance().getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId()).getDiscBag();
        player.getInventory().setItem(0, kitDisc(player, bag.equipped(DiscType.PUTTER), "putter"));
        player.getInventory().setItem(1, kitDisc(player, bag.equipped(DiscType.MID_RANGE), "midrange"));
        player.getInventory().setItem(2, kitDisc(player, bag.equipped(DiscType.DRIVER), "driver"));
        player.getInventory().setItem(6,tool(Material.COMPASS,"§eReturn to lie","lie"));
        player.getInventory().setItem(7,tool(Material.FEATHER,"§eSkip preview","skip"));
        player.getInventory().setItem(8,tool(Material.BOOK,"§aScorecard","score"));
        player.getInventory().setHeldItemSlot(0);
        player.sendMessage("§aChoose a disc, right-click to aim, then click to release. Swap hands changes backhand / forehand.");
    }

    private static ItemStack kitDisc(Player player, DiscModel model, String action) {
        ItemStack item = control(DiscItems.discItem(model.type(), model.displayName(), player, false), action);
        var meta = item.getItemMeta();
        var lore = new ArrayList<>(meta.getLore());
        lore.addFirst(ChatColor.GRAY + model.flightStats());
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
    @EventHandler public void interact(PlayerInteractEvent event) {
        if (NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).released(event)) return;
        if (event.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND || (event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR && event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) return;
        Player player=event.getPlayer(); RoundImpl round=round(player);
        if (round==null || event.getItem()==null) return;
        String action=event.getItem().getItemMeta().getPersistentDataContainer().get(key(),PersistentDataType.STRING);
        if (action==null) return;
        event.setCancelled(true);
        if (List.of("putter","midrange","driver").contains(action)) player.performCommand("throwdisc "+action+" "+techniques.getOrDefault(player.getUniqueId(),"backhand"));
        else player.performCommand("ndg "+action);
    }
    @EventHandler public void swap(PlayerSwapHandItemsEvent event) {
        if (round(event.getPlayer())==null) return;
        event.setCancelled(true);
        String next=techniques.getOrDefault(event.getPlayer().getUniqueId(),"backhand").equals("backhand")?"forehand":"backhand";
        techniques.put(event.getPlayer().getUniqueId(),next);
        event.getPlayer().sendMessage("§aThrow technique: "+next);
    }
    @EventHandler public void sneak(PlayerToggleSneakEvent event) { if(event.isSneaking() && RoundPresentation.isPreviewing(event.getPlayer())) RoundPresentation.cancel(event.getPlayer()); }
    @EventHandler public void drop(PlayerDropItemEvent event) { if(round(event.getPlayer())!=null) event.setCancelled(true); }
    @EventHandler public void inventory(InventoryClickEvent event) { if(event.getWhoClicked() instanceof Player p && round(p)!=null) event.setCancelled(true); }
    @EventHandler public void drag(InventoryDragEvent event) { if(event.getWhoClicked() instanceof Player p && round(p)!=null) event.setCancelled(true); }
    @EventHandler public void pickup(EntityPickupItemEvent event) { if(event.getEntity() instanceof Player p && round(p)!=null) event.setCancelled(true); }
    @EventHandler public void damage(EntityDamageEvent event) { if(event.getEntity() instanceof Player p && round(p)!=null) event.setCancelled(true); }
    @EventHandler public void food(FoodLevelChangeEvent event) { if(event.getEntity() instanceof Player p && round(p)!=null) event.setCancelled(true); }
    @EventHandler public void breakBlock(BlockBreakEvent event) { if(course(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler public void placeBlock(BlockPlaceEvent event) { if(course(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler public void explode(EntityExplodeEvent event) { if(course(event.getLocation().getWorld())) event.setCancelled(true); }
    @EventHandler public void blockExplode(BlockExplodeEvent event) { if(course(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler public void worldInit(org.bukkit.event.world.WorldInitEvent event) {
        if (event.getWorld().getName().startsWith("ndg_")) event.getWorld().setKeepSpawnInMemory(false);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { techniques.remove(event.getPlayer().getUniqueId()); }
}
