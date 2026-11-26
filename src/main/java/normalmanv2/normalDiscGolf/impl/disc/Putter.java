package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil;
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.technique.ThrowTechnique;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class Putter extends DiscImpl {



    public Putter(int speed, int glide, int turn, int fade, String discName) {
        super(speed, glide, turn, fade, discName, DiscType.PUTTER);
    }

    @Override
    public void handleThrow(Player player, PlayerSkills skills, String technique, BlockFace faceDirection, ThrowMechanic throwMechanic) {
        World world = player.getWorld();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location throwLoc = player.getEyeLocation().clone().add(direction.clone().multiply(0.35));
        ThrowMechanicImpl tm = (ThrowMechanicImpl) throwMechanic;

        ItemDisplay textDisplay = world.spawn(throwLoc, ItemDisplay.class, display -> {
            display.setItemStack(normalmanv2.normalDiscGolf.impl.resourcepack.DiscItems.discItem(this.getType(), this.getName(), player, true));


            display.setBillboard(Display.Billboard.CENTER);
            display.setCustomName(ChatColor.translateAlternateColorCodes('&', this.getName()));
            display.setCustomNameVisible(true);
            display.setPersistent(false);
        });

        int accuracyLevel = skills.getAccuracy().getLevel();
        int formLevel = skills.getForm().getLevel();
        int powerLevel = skills.getPower().getLevel();

        double intentionalPower = tm.getPower().get();
        double discBaseSpeed = Constants.PUTTER_BASE_SPEED * intentionalPower;
        double baseVelocity = discBaseSpeed * (1 + (powerLevel * Constants.POWER_ADJUSTMENT));
        double finalVelocity = baseVelocity * (1 + (formLevel * Constants.FORM_ADJUSTMENT));

        Vector velocity = direction.clone().multiply(finalVelocity);

        double maxSpread = Math.max(0.0, 0.05 - (accuracyLevel * Constants.ACCURACY_ADJUSTMENT));
        velocity.setX(velocity.getX() + (Math.random() * maxSpread - maxSpread / 2));
        velocity.setZ(velocity.getZ() + (Math.random() * maxSpread - maxSpread / 2));

        applyDiscPhysics(player, textDisplay, velocity, 60, technique, faceDirection);
    }

    @Override
    public void applyDiscPhysics(Player player, ItemDisplay display, Vector velocity, int maxTicks, String technique, BlockFace direction) {
        DiscFlightEngine.start(this, player, display, velocity, maxTicks, technique, direction);
    }
}
