package normalmanv2.normalDiscGolf.disc;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.api.API;
import normalmanv2.normalDiscGolf.player.PlayerSkills;
import normalmanv2.normalDiscGolf.disc.util.MathUtil;
import normalmanv2.normalDiscGolf.technique.ThrowTechnique;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class Driver extends Disc {
    private final NormalDiscGolf normalDiscGolf;
    private BukkitTask discTask;

    public Driver(int speed, int glide, int turn, int fade, NormalDiscGolf normalDiscGolf) {
        super(speed, glide, turn, fade, DiscType.DRIVER);
        this.normalDiscGolf = normalDiscGolf;
    }

    @Override
    public void handleThrow(Player player, PlayerSkills skills, String technique) {

        World world = player.getWorld();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location throwLoc = player.getEyeLocation().add(direction.multiply(2));

        TextDisplay display = world.spawn(throwLoc.clone(), TextDisplay.class);
        display.setText("DRIVER DISC");
        display.setBillboard(Display.Billboard.CENTER);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setCustomNameVisible(false);
        display.setBackgroundColor(Color.fromARGB(0x80333333));
        display.setSeeThrough(true);
        display.setShadowed(true);

        int accuracyLevel = skills.getAccuracy().getLevel();
        int formLevel = skills.getForm().getLevel();
        int powerLevel = skills.getPower().getLevel();

        double discBaseSpeed = 2.5;
        double baseVelocity = discBaseSpeed * (1 + (powerLevel * 0.05));
        double finalVelocity = baseVelocity * (1 + (formLevel * 0.02));

        Vector velocity = direction.multiply(finalVelocity);

        double maxSpread = 0.05 - (accuracyLevel * 0.5);
        velocity.setX(velocity.getX() + (Math.random() * maxSpread - maxSpread / 2));
        velocity.setZ(velocity.getZ() + (Math.random() * maxSpread - maxSpread / 2));

        applyDiscPhysics(player, display, velocity, 100, technique);

    }

    @Override
    public void applyDiscPhysics(Player player, TextDisplay discDisplay, Vector initialVelocity, int maxTicks, String technique) {
        Vector currentVelocity = initialVelocity.clone();
        final int[] tickCount = {0};

        final ThrowTechnique throwTechnique = API.getThrowTechniqueRegistry().getTechnique(technique);
        this.discTask = Bukkit.getScheduler().runTaskTimer(normalDiscGolf, () -> {

            Location currentLocation = discDisplay.getLocation();
            currentLocation.add(currentVelocity);
            throwTechnique.applyPhysics(this, currentVelocity, tickCount[0], maxTicks);
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
