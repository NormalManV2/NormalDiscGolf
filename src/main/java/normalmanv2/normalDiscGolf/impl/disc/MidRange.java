package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.event.GoalScoreEvent;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
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

public class MidRange extends DiscImpl {

    private final NormalDiscGolfPlugin plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
    private BukkitTask discTask;

    public MidRange(int speed, int glide, int turn, int fade, String discName) {
        super(speed, glide, turn, fade, discName, DiscType.MID_RANGE);
        NDGManager.getInstance().getDiscRegistry().register(discName, this);
    }

    @Override
    public void handleThrow(Player player, PlayerSkills skills, String technique, BlockFace faceDirection, ThrowMechanic throwMechanic) {

        World world = player.getWorld();
        Vector direction = player.getEyeLocation().getDirection().normalize();
        Location throwLoc = player.getEyeLocation().add(direction.multiply(2));
        ThrowMechanicImpl tm = (ThrowMechanicImpl) throwMechanic;

        ItemDisplay textDisplay = world.spawn(throwLoc, ItemDisplay.class, display -> {
            display.setItemStack(new ItemStack(Material.POPPED_CHORUS_FRUIT));
            ItemMeta displayMeta = display.getItemStack().getItemMeta();

            displayMeta.setCustomModelData(1);
            display.getItemStack().setItemMeta(displayMeta);

            display.setBillboard(Display.Billboard.CENTER);
            display.setCustomName(ChatColor.translateAlternateColorCodes('&', this.getName()));
            display.setCustomNameVisible(true);
        });

        int accuracyLevel = skills.getAccuracy().getLevel();
        int formLevel = skills.getForm().getLevel();
        int powerLevel = skills.getPower().getLevel();

        double intentionalPower = tm.getPower().get();
        double discBaseSpeed = Constants.MID_BASE_SPEED * intentionalPower;
        double baseVelocity = discBaseSpeed * (1 + (powerLevel * Constants.POWER_ADJUSTMENT));
        double finalVelocity = baseVelocity * (1 + (formLevel * Constants.FORM_ADJUSTMENT));

        Vector velocity = direction.multiply(finalVelocity);

        double maxSpread = 0.05 - (accuracyLevel * Constants.ACCURACY_ADJUSTMENT);
        velocity.setX(velocity.getX() + (Math.random() * maxSpread - maxSpread / 2));
        velocity.setZ(velocity.getZ() + (Math.random() * maxSpread - maxSpread / 2));

        applyDiscPhysics(player, textDisplay, velocity, 80, technique, faceDirection);
    }

    @Override
    public void applyDiscPhysics(Player player, ItemDisplay discDisplay, Vector initialVelocity, int maxTicks, String technique, BlockFace direction) {
        Vector currentVelocity = initialVelocity.clone();
        final int[] tickCount = {0};

        final ThrowTechnique throwTechnique = NDGManager.getInstance().getThrowTechniqueRegistry().get(technique);
        this.discTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            Location currentLocation = discDisplay.getLocation();
            currentLocation.add(currentVelocity);
            throwTechnique.applyPhysics(this, currentVelocity, tickCount[0], maxTicks, direction);
            discDisplay.teleport(currentLocation);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, currentLocation, 5);

            if (MathUtil.detectGoalCollision(player.getWorld(), discDisplay.getLocation(), currentVelocity) || tickCount[0] > maxTicks) {
                Vector throwDirection = initialVelocity.clone().normalize();
                Location teleportLocation = currentLocation.clone().subtract(throwDirection.multiply(1.5));

                player.teleport(teleportLocation);

                FFARound round = (FFARound) NDGManager.getInstance().getRoundHandler().getActiveRounds().get(0);
                System.out.println("Disc hit the goal!");
                Bukkit.getPluginManager().callEvent(new GoalScoreEvent(player, 1, round));
                if (this.discTask == null) {
                    discDisplay.remove();
                    return;
                }
                this.discTask.cancel();
                this.discTask = null;
                Bukkit.getScheduler().runTaskLater(plugin, discDisplay::remove, 100);
                return;
            }

            if (MathUtil.detectCollision(player.getWorld(), discDisplay.getLocation(), currentVelocity) || tickCount[0] > maxTicks) {
                Vector throwDirection = initialVelocity.clone().normalize();
                Location teleportLocation = currentLocation.clone().subtract(throwDirection.multiply(1.5));

                player.teleport(teleportLocation);

                if (this.discTask == null) {
                    discDisplay.remove();
                    return;
                }

                this.discTask.cancel();
                this.discTask = null;
                Bukkit.getScheduler().runTaskLater(plugin, discDisplay::remove, 100);
                return;
            }

            tickCount[0]++;
        }, 0, 1);
    }
}
