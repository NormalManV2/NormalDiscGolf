package normalmanv2.normalDiscGolf.impl.player.session;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PlayerStateSnapshot {

    private final Location location;
    private final GameMode gameMode;
    private final boolean allowFlight;
    private final boolean flying;
    private final boolean gliding;
    private final boolean invulnerable;
    private final boolean collidable;
    private final int foodLevel;
    private final float saturation;
    private final float exhaustion;
    private final double health;
    private final int fireTicks;
    private final float fallDistance;
    private final int maximumAir;
    private final int remainingAir;
    private final int totalExperience;
    private final int level;
    private final float exp;
    private final int heldItemSlot;
    private final ItemStack[] storageContents;
    private final ItemStack[] armorContents;
    private final ItemStack offHandItem;
    private final List<PotionEffect> potionEffects;

    private PlayerStateSnapshot(Player player) {
        this.location = player.getLocation().clone();
        this.gameMode = player.getGameMode();
        this.allowFlight = player.getAllowFlight();
        this.flying = player.isFlying();
        this.gliding = player.isGliding();
        this.invulnerable = player.isInvulnerable();
        this.collidable = player.isCollidable();
        this.foodLevel = player.getFoodLevel();
        this.saturation = player.getSaturation();
        this.exhaustion = player.getExhaustion();
        this.health = player.getHealth();
        this.fireTicks = player.getFireTicks();
        this.fallDistance = player.getFallDistance();
        this.maximumAir = player.getMaximumAir();
        this.remainingAir = player.getRemainingAir();
        this.totalExperience = player.getTotalExperience();
        this.level = player.getLevel();
        this.exp = player.getExp();
        this.heldItemSlot = player.getInventory().getHeldItemSlot();
        this.storageContents = cloneItems(player.getInventory().getStorageContents());
        this.armorContents = cloneItems(player.getInventory().getArmorContents());
        this.offHandItem = cloneItem(player.getInventory().getItemInOffHand());
        this.potionEffects = new ArrayList<>(player.getActivePotionEffects());
    }

    public static PlayerStateSnapshot capture(Player player) {
        return new PlayerStateSnapshot(player);
    }

    public void restore(Player player) {
        player.leaveVehicle();

        if (this.location.getWorld() != null) {
            player.teleport(this.location);
        }

        player.setGameMode(this.gameMode);
        player.setAllowFlight(this.allowFlight);
        player.setFlying(this.allowFlight && this.flying);
        player.setGliding(this.gliding);
        player.setInvulnerable(this.invulnerable);
        player.setCollidable(this.collidable);
        player.setFoodLevel(this.foodLevel);
        player.setSaturation(this.saturation);
        player.setExhaustion(this.exhaustion);
        player.setFireTicks(this.fireTicks);
        player.setFallDistance(this.fallDistance);
        player.setMaximumAir(this.maximumAir);
        player.setRemainingAir(Math.min(this.remainingAir, this.maximumAir));
        player.setTotalExperience(this.totalExperience);
        player.setLevel(this.level);
        player.setExp(this.exp);
        player.setHealth(Math.min(this.health, player.getMaxHealth()));

        player.getInventory().setStorageContents(cloneItems(this.storageContents));
        player.getInventory().setArmorContents(cloneItems(this.armorContents));
        player.getInventory().setItemInOffHand(cloneItem(this.offHandItem));
        player.getInventory().setHeldItemSlot(this.heldItemSlot);
        player.updateInventory();

        restorePotionEffects(player, this.potionEffects);
    }

    private static void restorePotionEffects(Player player, Collection<PotionEffect> potionEffects) {
        for (PotionEffect potionEffect : player.getActivePotionEffects()) {
            player.removePotionEffect(potionEffect.getType());
        }

        player.addPotionEffects(potionEffects);
    }

    private static ItemStack[] cloneItems(ItemStack[] items) {
        ItemStack[] clonedItems = new ItemStack[items.length];
        for (int index = 0; index < items.length; index++) {
            clonedItems[index] = cloneItem(items[index]);
        }
        return clonedItems;
    }

    private static ItemStack cloneItem(ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }
}
