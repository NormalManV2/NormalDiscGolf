package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;
import normalmanv2.normalDiscGolf.impl.disc.DiscFlightResolver;
import normalmanv2.normalDiscGolf.impl.disc.DiscModel;
import normalmanv2.normalDiscGolf.impl.disc.ArcadeDisc;
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
    private static final int DISC_HOTBAR_SLOTS = 6;
    private static final int ARCADE_FIRST_SLOT = 3;
    private static NamespacedKey key() { return new NamespacedKey(NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class), "control"); }
    private final Map<UUID, String> techniques = new HashMap<>();
    private RoundImpl round(Player player) { var round = DiscFlightResolver.findActiveRound(player); return round instanceof RoundImpl impl ? impl : null; }
    private boolean course(World world) { return NDGManager.getInstance().getRoundHandler().isCourseWorld(world); }
    private static ItemStack control(ItemStack item, String action) {
        var meta=item.getItemMeta(); meta.getPersistentDataContainer().set(key(), PersistentDataType.STRING, action);
        meta.setLore(List.of("§7Right-click to use")); item.setItemMeta(meta); return item;
    }
    private static ItemStack tool(Material material, String title, String action) {
        ItemStack item=new ItemStack(material); var meta=item.getItemMeta(); meta.setDisplayName(title); item.setItemMeta(meta); return control(item,action);
    }
    public static void giveKit(Player player, RoundImpl round) {
        player.getInventory().clear();
        var bag = round instanceof CompetitionRound competition ? competition.loanerBag()
                : NDGManager.getInstance().getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId()).getDiscBag();
        round.play().setPackedDiscs(player.getUniqueId(), bag.packed());
        var discs = round.play().throwableDiscs(player.getUniqueId());
        int slots = round instanceof ArcadeRound ? ARCADE_FIRST_SLOT : DISC_HOTBAR_SLOTS;
        for (int i = 0; i < Math.min(slots, discs.size()); i++)
            player.getInventory().setItem(i, kitDisc(player, discs.get(i)));
        if (round instanceof ArcadeRound arcade)
            for (var disc : ArcadeDisc.all()) refreshArcadeDisc(player, arcade, disc);
        player.getInventory().setItem(6,tool(Material.COMPASS,"§eReturn to lie","lie"));
        player.getInventory().setItem(7,tool(Material.BUNDLE,"§bRound Disc Bag","bag"));
        player.getInventory().setItem(8,tool(Material.BOOK,"§aScorecard","score"));
        player.getInventory().setHeldItemSlot(round.play().usesOneDisc() || round.play().usesRoulette() ? 7 : 0);
        if (round.play().usesOneDisc())
            player.sendMessage("§aOne Disc Challenge. Open the bundle or use /ndg bag to lock one disc for the whole round.");
        else if (round.play().usesRoulette())
            player.sendMessage("§dDisc Roulette. Each turn draws a disc from your bag. Right-click it to aim, then click to release.");
        else player.sendMessage("§aChoose a disc, right-click to aim, then click to release. Swap hands changes backhand / forehand.");
        player.sendMessage("§7Sneak + swap hands cycles the release angle. Use /ndg shot for putting, timing, preview and wind.");
    }

    private static ItemStack kitDisc(Player player, DiscModel model) {
        ItemStack item = control(DiscItems.discItem(model, player, false), "disc:" + model.id());
        var meta = item.getItemMeta();
        var lore = new ArrayList<>(meta.getLore());
        lore.addFirst(ChatColor.GRAY + model.flightStats());
        lore.add(ChatColor.GRAY + "Swap hands to change throw technique");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static DiscModel heldDisc(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String action = item.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.STRING);
        if (action == null || !action.startsWith("disc:")) return null;
        String id = action.substring(5);
        var arcade = ArcadeDisc.find(id);
        return arcade == null ? NDGManager.getInstance().getDiscCatalog().find(id) : arcade.model();
    }

    public static boolean selectDisc(Player player, DiscModel model) {
        var inventory = player.getInventory();
        var activeRound = DiscFlightResolver.findActiveRound(player);
        if (activeRound instanceof DiscRouletteRound round) {
            player.sendMessage(ChatColor.YELLOW + round.play().discRestriction(player.getUniqueId()));
            return false;
        }
        if (activeRound instanceof OneDiscRound round) {
            var play = round.play();
            UUID id = player.getUniqueId();
            DiscModel locked = play.lockedDisc(id);
            if (locked != null && !locked.equals(model)) {
                player.sendMessage(ChatColor.RED + "Your disc is locked to " + locked.displayName() + " for this round.");
                return false;
            }
            if (locked == null && !play.lockDisc(id, model)) return false;
            NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).cancelMeter(id);
            equipOnlyDisc(player, model);
            player.sendMessage(ChatColor.GREEN + "Locked " + model.displayName() + " for this One Disc Challenge.");
            return true;
        }
        int slots = activeRound instanceof ArcadeRound ? ARCADE_FIRST_SLOT : DISC_HOTBAR_SLOTS;
        int slot = slots - 1;
        for (int i = 0; i < slots; i++) {
            if (heldDisc(inventory.getItem(i)) == model) {
                slot = i;
                break;
            }
        }
        NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).cancelMeter(player.getUniqueId());
        inventory.setItem(slot, kitDisc(player, model));
        inventory.setHeldItemSlot(slot);
        player.sendMessage(ChatColor.AQUA + "Selected " + model.displayName() + ".");
        return true;
    }

    public static void equipOnlyDisc(Player player, DiscModel model) {
        var inventory = player.getInventory();
        for (int i = 0; i < DISC_HOTBAR_SLOTS; i++) inventory.clear(i);
        if (model != null) {
            inventory.setItem(0, kitDisc(player, model));
            inventory.setHeldItemSlot(0);
        }
        NDGManager.getInstance().getGuiManager().refreshForPack(player);
    }

    public static void refreshDiscModels(Player player) {
        var activeRound = DiscFlightResolver.findActiveRound(player);
        for (int i = 0; i < DISC_HOTBAR_SLOTS; i++) {
            DiscModel model = heldDisc(player.getInventory().getItem(i));
            if (model == null) continue;
            ArcadeDisc special = ArcadeDisc.find(model.id());
            if (special == null) player.getInventory().setItem(i, kitDisc(player, model));
            else if (activeRound instanceof ArcadeRound arcade) refreshArcadeDisc(player, arcade, special);
        }
    }

    public static void refreshArcadeDisc(Player player, ArcadeRound round, ArcadeDisc disc) {
        var item = control(DiscItems.arcadeItem(disc, player, false), "disc:" + disc.model().id());
        var meta = item.getItemMeta();
        int remaining = round.remaining(player.getUniqueId(), disc);
        meta.setDisplayName((remaining > 0 ? ChatColor.LIGHT_PURPLE : ChatColor.GRAY) + disc.model().displayName());
        meta.setLore(List.of(ChatColor.GOLD + "" + remaining + "/" + round.chargeLimit() + " charges remaining",
                ChatColor.GRAY + disc.description(), ChatColor.GRAY + "Right-click to aim"));
        item.setItemMeta(meta);
        player.getInventory().setItem(ARCADE_FIRST_SLOT + disc.ordinal(), item);
    }
    @EventHandler public void interact(PlayerInteractEvent event) {
        if (NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).released(event)) return;
        if (event.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND || (event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR && event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) return;
        Player player=event.getPlayer(); RoundImpl round=round(player);
        if (round==null || event.getItem()==null) return;
        String action=event.getItem().getItemMeta().getPersistentDataContainer().get(key(),PersistentDataType.STRING);
        if (action==null) return;
        event.setCancelled(true);
        if (action.startsWith("disc:")) player.performCommand("throwdisc "+action.substring(5)+" "+techniques.getOrDefault(player.getUniqueId(),"backhand"));
        else player.performCommand("ndg "+action);
    }
    @EventHandler public void swap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        RoundImpl round = round(player);
        if (round == null) return;
        event.setCancelled(true);
        if (RoundPresentation.isPreviewing(player)) return;
        if (player.isSneaking()) {
            var setup = round.play().setup(player.getUniqueId());
            var angle = setup.angle().next();
            round.play().setSetup(player.getUniqueId(), setup.withAngle(angle));
            player.sendMessage(ChatColor.GREEN + "Release angle: " + angle.name().toLowerCase(java.util.Locale.ROOT));
            return;
        }
        NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).cancelMeter(player.getUniqueId());
        String next=techniques.getOrDefault(event.getPlayer().getUniqueId(),"backhand").equals("backhand")?"forehand":"backhand";
        techniques.put(event.getPlayer().getUniqueId(),next);
        event.getPlayer().sendMessage("§aThrow technique: "+next);
    }
    @EventHandler public void changeDisc(PlayerItemHeldEvent event) {
        if (round(event.getPlayer()) != null)
            NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).cancelMeter(event.getPlayer().getUniqueId());
    }
    @EventHandler public void sneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (RoundPresentation.isPreviewing(player)) RoundPresentation.cancel(player);
        RoundImpl round = round(player);
        if (round != null) round.play().spectating().skip(player.getUniqueId());
    }
    @EventHandler(ignoreCancelled = true)
    public void spectatorTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.SPECTATE) return;
        Player player = event.getPlayer();
        RoundImpl round = round(player);
        if (round != null && !round.play().spectating().permitsSpectatorTeleport(player.getUniqueId(), event.getTo()))
            event.setCancelled(true);
    }
    @EventHandler public void recoverSkyFall(PlayerMoveEvent event) {
        var to = event.getTo();
        if (to == null || to.getY() >= CourseGrid.SKY_RECOVERY_HEIGHT
                || event instanceof PlayerTeleportEvent || event.isCancelled()) return;
        Player player = event.getPlayer();
        RoundImpl round = round(player);
        if (round == null || !player.getWorld().getName().equals(round.getId()) || RoundPresentation.isPreviewing(player)
                || round.getCourse().grid().terrain() != CourseTerrain.SKY) return;
        var lie = round.play().lie(round.play().team(player.getUniqueId()));
        if (lie != null) {
            player.setFallDistance(0);
            player.setVelocity(new org.bukkit.util.Vector());
            event.setTo(lie);
        }
    }
    @EventHandler public void drop(PlayerDropItemEvent event) { if(round(event.getPlayer())!=null) event.setCancelled(true); }
    @EventHandler public void inventory(InventoryClickEvent event) { if(event.getWhoClicked() instanceof Player p && round(p)!=null) event.setCancelled(true); }
    @EventHandler public void drag(InventoryDragEvent event) { if(event.getWhoClicked() instanceof Player p && round(p)!=null) event.setCancelled(true); }
    @EventHandler public void pickup(EntityPickupItemEvent event) { if(event.getEntity() instanceof Player p && round(p)!=null) event.setCancelled(true); }
    @EventHandler public void damage(EntityDamageEvent event) { if(event.getEntity() instanceof Player p && round(p)!=null) event.setCancelled(true); }
    @EventHandler public void food(FoodLevelChangeEvent event) { if(event.getEntity() instanceof Player p && round(p)!=null) event.setCancelled(true); }
    @EventHandler public void breakBlock(BlockBreakEvent event) { if(course(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler public void placeBlock(BlockPlaceEvent event) { if(course(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler public void containCourseLiquids(BlockFromToEvent event) {
        if (event.getBlock().isLiquid() && course(event.getBlock().getWorld())) event.setCancelled(true);
    }
    @EventHandler public void explode(EntityExplodeEvent event) { if(course(event.getLocation().getWorld())) event.setCancelled(true); }
    @EventHandler public void blockExplode(BlockExplodeEvent event) { if(course(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void weatherLightning(org.bukkit.event.weather.LightningStrikeEvent event) {
        if (event.getCause() == org.bukkit.event.weather.LightningStrikeEvent.Cause.WEATHER && course(event.getWorld()))
            event.setCancelled(true);
    }
    @EventHandler public void worldInit(org.bukkit.event.world.WorldInitEvent event) {
        if (event.getWorld().getName().startsWith("ndg_")) event.getWorld().setKeepSpawnInMemory(false);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { techniques.remove(event.getPlayer().getUniqueId()); }
}
