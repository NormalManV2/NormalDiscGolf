package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.api.NDGApi;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.technique.ThrowTechnique;
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
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

public class Putter extends Disc {

    private final NormalDiscGolf plugin;
    private final NDGApi api = NDGApi.getInstance();
    private BukkitTask discTask;

    public Putter(int speed, int glide, int turn, int fade, String discName, NormalDiscGolf plugin) {
        super(speed, glide, turn, fade, discName, DiscType.PUTTER);
        this.plugin = plugin;
    }

    @Override
    public void handleThrow(Player player, PlayerSkills skills, String technique, BlockFace faceDirection) {
        World world = player.getWorld();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location throwLoc = player.getEyeLocation().add(direction.multiply(2));

        ItemDisplay display = world.spawn(throwLoc, ItemDisplay.class);
        display.setItemStack(new ItemStack(Material.POPPED_CHORUS_FRUIT));

        Vector3f translation = new Vector3f();
        AxisAngle4f leftRotation = new AxisAngle4f();
        Vector3f scale = new Vector3f(1, 1, 1);
        AxisAngle4f rightRotation = new AxisAngle4f();

        Transformation transformation = new Transformation(translation, leftRotation, scale, rightRotation);

        // display.setTransformation(transformation);
        display.setBillboard(Display.Billboard.CENTER);
        display.setCustomName(ChatColor.translateAlternateColorCodes('&', this.getDiscName()));
        display.setCustomNameVisible(true);
        display.setPersistent(false);

        int accuracyLevel = skills.getAccuracy().getLevel();
        int formLevel = skills.getForm().getLevel();
        int powerLevel = skills.getPower().getLevel();

        double discBaseSpeed = 1.3;
        double baseVelocity = discBaseSpeed * (1 + (powerLevel * 0.05));
        double finalVelocity = baseVelocity * (1 + (formLevel * 0.02));

        Vector velocity = direction.multiply(finalVelocity);

        double maxSpread = 0.05 - (accuracyLevel * 0.5);
        velocity.setX(velocity.getX() + (Math.random() * maxSpread - maxSpread / 2));
        velocity.setZ(velocity.getZ() + (Math.random() * maxSpread - maxSpread / 2));

        applyDiscPhysics(player, display, velocity, 60, technique, faceDirection);
    }

    @Override
    public void applyDiscPhysics(Player player, ItemDisplay discDisplay, Vector initialVelocity, int maxTicks, String technique, BlockFace direction) {
        Vector currentVelocity = initialVelocity.clone();
        final int[] tickCount = {0};

        final ThrowTechnique throwTechnique = api.getThrowTechniqueRegistry().getTechnique(technique);
        this.discTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            Location currentLocation = discDisplay.getLocation();
            currentLocation.add(currentVelocity);
            throwTechnique.applyPhysics(this, currentVelocity, tickCount[0], maxTicks, direction);
            // REMOVED : MathUtil.adjustFlightPath(currentVelocity, tickCount[0], maxTicks, this, technique);
            discDisplay.teleport(currentLocation);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, currentLocation, 5);

            // Conditional for when the disc task should no longer run, ie: Disc hits the ground or an obstacle.

            if (MathUtil.detectCollision(player.getWorld(), discDisplay.getLocation(), currentVelocity) || tickCount[0] > maxTicks) {
                Vector throwDirection = initialVelocity.clone().normalize();
                Location teleportLocation = currentLocation.clone().subtract(throwDirection.multiply(1.5));

                player.teleport(teleportLocation);
                discTask.cancel();
                this.discTask = null;
                return;
            }

            tickCount[0]++;
        }, 0, 1);
    }
}
