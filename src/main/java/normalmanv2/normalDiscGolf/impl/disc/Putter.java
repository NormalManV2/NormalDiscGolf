package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.disc.DiscType;
import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.event.GoalScoreEvent;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil;
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

    private final NormalDiscGolfPlugin plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
    private BukkitTask discTask;

    public Putter(int speed, int glide, int turn, int fade, String discName) {
        super(speed, glide, turn, fade, discName, DiscType.PUTTER);
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
            display.setPersistent(false);
        });

        int accuracyLevel = skills.getAccuracy().getLevel();
        int formLevel = skills.getForm().getLevel();
        int powerLevel = skills.getPower().getLevel();

        double intentionalPower = tm.getPower().get();
        double discBaseSpeed = Constants.PUTTER_BASE_SPEED * intentionalPower;
        double baseVelocity = discBaseSpeed * (1 + (powerLevel * Constants.POWER_ADJUSTMENT));
        double finalVelocity = baseVelocity * (1 + (formLevel * Constants.FORM_ADJUSTMENT));

        Vector velocity = direction.multiply(finalVelocity);

        double maxSpread = Math.max(0.0, 0.05 - (accuracyLevel * Constants.ACCURACY_ADJUSTMENT));
        velocity.setX(velocity.getX() + (Math.random() * maxSpread - maxSpread / 2));
        velocity.setZ(velocity.getZ() + (Math.random() * maxSpread - maxSpread / 2));

        applyDiscPhysics(player, textDisplay, velocity, 60, technique, faceDirection);
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

            boolean goalScored = MathUtil.detectGoalCollision(player.getWorld(), currentLocation, currentVelocity);
            boolean flightEnded = goalScored
                    || MathUtil.detectCollision(player.getWorld(), currentLocation, currentVelocity)
                    || tickCount[0] >= maxTicks;

            if (flightEnded) {
                this.resolveFlight(player, discDisplay, currentLocation, initialVelocity, goalScored);
                return;
            }

            tickCount[0]++;
        }, 0, 1);
    }

    private void resolveFlight(Player player, ItemDisplay discDisplay, Location currentLocation, Vector initialVelocity, boolean goalScored) {
        GameRound round = this.getActiveRound(player);
        if (round != null && goalScored) {
            this.handleGoalScore(player, round);
        } else if (!goalScored) {
            Vector throwDirection = initialVelocity.clone().normalize();
            Location teleportLocation = currentLocation.clone().subtract(throwDirection.multiply(1.5));
            player.teleport(teleportLocation);
        }

        this.cleanupDiscFlight(discDisplay);

        if (round != null && !round.isOver()) {
            round.nextTurn();
        }
    }

    private GameRound getActiveRound(Player player) {
        return NDGManager.getInstance().getRoundHandler().getActiveRounds().stream()
                .filter(round -> round.containsPlayer(player.getUniqueId()))
                .findFirst()
                .orElse(null);
    }

    private void cleanupDiscFlight(ItemDisplay discDisplay) {
        if (this.discTask != null) {
            this.discTask.cancel();
            this.discTask = null;
        }
        Bukkit.getScheduler().runTaskLater(plugin, discDisplay::remove, 100);
    }

    private void handleGoalScore(Player player, GameRound round) {
        Bukkit.getPluginManager().callEvent(new GoalScoreEvent(player, round.getCurrentHole(), round));
    }
}
