package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.impl.util.BukkitCompatibility;
import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.event.CourseEvent;
import normalmanv2.normalDiscGolf.impl.disc.ShotSetup.Stance;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil;
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.resourcepack.DiscItems;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.inventory.MainHand;
import org.bukkit.scheduler.*;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public final class DiscFlightEngine {
    private DiscFlightEngine() {}

    static void launch(Player player, PlayerSkills skills, ThrowMechanicImpl mechanic) {
        DiscImpl disc = mechanic.getDisc();
        RoundImpl round = activeShot(player);
        Stance stance = mechanic.stance();
        double control = stance.isTrickShot() ? TrickFlight.releaseControl(skills.stanceLevel(stance)) : 1;
        double yaw = Math.toRadians(skills.releaseSpreadDegrees(mechanic.recoveryLie()))
                * control * mechanic.spreadMultiplier() * (2 * Math.random() - 1);
        DiscTrajectory trajectory = trajectory(player, skills, mechanic, round, yaw, false);
        ItemDisplay display = BukkitCompatibility.spawn(trajectory.location(), ItemDisplay.class, item -> {
            var special = mechanic.arcadeDisc();
            item.setItemStack(special == null ? DiscItems.discItem(disc.getType(), disc.getName(), player, true)
                    : DiscItems.arcadeItem(special, player, true));
            item.setBillboard(Display.Billboard.CENTER);
            item.setCustomName(ChatColor.translateAlternateColorCodes('&', disc.getName()));
            item.setCustomNameVisible(true);
            item.setPersistent(false);
        });
        start(disc, player, display, mechanic.getThrowTechnique(), round, trajectory, stance);
    }

    public static void preview(Player player, PlayerSkills skills, ThrowMechanicImpl mechanic, RoundImpl round) {
        DiscTrajectory trajectory = trajectory(player, skills, mechanic, round, 0, true);
        drawPreview(player, mechanic, trajectory);
    }

    public static void previewPractice(Player player, ThrowMechanicImpl mechanic, Location basket, BoundingBox area) {
        drawPreview(player, mechanic, practiceTrajectory(player, mechanic, basket, area, 0));
    }

    private static void drawPreview(Player player, ThrowMechanicImpl mechanic, DiscTrajectory trajectory) {
        Location origin = trajectory.location();
        Stance stance = mechanic.stance();
        Color color = switch (stance) {
            case PUTT -> Color.LIME;
            case FLEX -> Color.YELLOW;
            case TOMAHAWK -> Color.FUCHSIA;
            case THUMBER -> Color.fromRGB(0xae7aff);
            default -> Color.AQUA;
        };
        var dust = new Particle.DustOptions(color, .65f);
        var groundDust = new Particle.DustOptions(Color.ORANGE, .8f);
        // Sample longer arcs less often so there are still only a few particles per update.
        int maxTicks = stance.isTrickShot() ? 48 : 24;
        int interval = stance.isTrickShot() ? 3 : 2;
        double range = stance.isTrickShot() ? 42 : 18;
        for (int tick = 1; tick <= maxTicks; tick++) {
            DiscTrajectory.Step step = trajectory.advance();
            if (step.location().distanceSquared(origin) > range * range) break;
            boolean contact = step.impact() != GroundPhysics.Impact.NONE || step.motion() != GroundPhysics.Motion.AIR;
            if (tick % interval == 0 || step.impact() != GroundPhysics.Impact.NONE || step.finished())
                player.spawnParticle(BukkitCompatibility.DUST, step.location(), 1, 0, 0, 0, 0, contact ? groundDust : dust);
            if (step.finished()) break;
        }
    }

    private static DiscTrajectory trajectory(Player player, PlayerSkills skills, ThrowMechanicImpl mechanic,
                                             RoundImpl round, double yaw, boolean preview) {
        return trajectory(player, skills, mechanic, round.play().wind(), arcade -> collision(round, preview, arcade), yaw);
    }

    private static DiscTrajectory practiceTrajectory(Player player, ThrowMechanicImpl mechanic, Location basket,
                                                     BoundingBox area, double yaw) {
        var target = BoundingBox.of(basket, 1.3, 1.0, 1.3);
        return trajectory(player, mechanic.effectiveSkills(), mechanic, HoleWind.CALM,
                arcade -> (from, velocity) -> MathUtil.tracePractice(from, velocity, target, area), yaw);
    }

    private static DiscTrajectory trajectory(Player player, PlayerSkills skills, ThrowMechanicImpl mechanic, HoleWind wind,
                                             Function<ArcadeFlight, BiFunction<Location, Vector, MathUtil.FlightHit>> collision,
                                             double yaw) {
        DiscImpl disc = mechanic.getDisc();
        String technique = mechanic.getThrowTechnique();
        var physics = NDGManager.getInstance().getThrowTechniqueRegistry().get(technique);
        BlockFace facing = player.getFacing();
        Location eye = player.getEyeLocation();
        Stance stance = mechanic.stance();
        ShotSetup.Angle angle = mechanic.releaseAngle();
        boolean forehand = technique.equals("forehand");
        double drag = skills.flightDrag();
        double turn = skills.turnMultiplier(), fade = skills.fadeMultiplier(technique);
        boolean mirrored = stance.isOverhand() ? player.getMainHand() == MainHand.LEFT : forehand;
        TrickFlight trick = stance.isTrickShot() ? new TrickFlight(stance, disc, angle, mirrored, drag) : null;
        Vector direction = eye.getDirection().rotateAroundY(yaw);
        boolean putting = mechanic.isPutting();
        boolean roller = stance == Stance.ROLLER;
        if (trick != null) direction = trick.aim(direction);
        else if (roller && direction.getY() > -.22) direction.setY(-.22).normalize();
        Location origin = eye.add(direction.clone().multiply(.35));
        int maxTicks = DiscTrajectory.maxTicks(disc.getType(), putting);
        int rollerLevel = roller ? skills.getRoller().getLevel() : 0;
        double rollCurve = (angle.bank() == 0 ? .009 : angle.bank() * .028)
                * (forehand ? -1 : 1) / (1 + .12 * Math.max(0, rollerLevel - 1));
        GroundPhysics ground = new GroundPhysics(putting, rollerLevel, rollCurve);
        Vector velocity = direction.multiply(DiscTrajectory.launchSpeed(disc, skills, mechanic.getPower().get(), putting));
        if (trick != null) velocity.multiply(trick.speedScale());
        var arcade = mechanic.arcadeDisc() == null ? null : new ArcadeFlight(mechanic.arcadeDisc());
        return new DiscTrajectory(origin, velocity, maxTicks, collision.apply(arcade), (motion, tick) -> {
            if (trick != null) {
                trick.advance(motion, tick);
                motion.add(wind.acceleration(tick));
                return;
            }
            if (putting) {
                motion.multiply(.96);
                motion.setY(motion.getY() - .014);
            } else if (roller) {
                motion.multiply(drag);
                motion.setY(motion.getY() - .027);
            } else {
                physics.applyPhysics(disc, motion, tick, maxTicks, facing, drag, turn, fade);
            }
            DiscTrajectory.applyReleasePhysics(motion, tick, angle, forehand, putting, wind);
        }, ground, arcade);
    }

    private static BiFunction<Location, Vector, MathUtil.FlightHit> collision(RoundImpl round, boolean preview, ArcadeFlight arcade) {
        return (from, velocity) -> {
            if (preview) {
                Location to = from.clone().add(velocity);
                for (int x = Math.min(from.getBlockX(), to.getBlockX()) >> 4; x <= Math.max(from.getBlockX(), to.getBlockX()) >> 4; x++) {
                    for (int z = Math.min(from.getBlockZ(), to.getBlockZ()) >> 4; z <= Math.max(from.getBlockZ(), to.getBlockZ()) >> 4; z++) {
                        if (!from.getWorld().isChunkLoaded(x, z)) return new MathUtil.FlightHit(from, false);
                    }
                }
            }
            return MathUtil.trace(round, from, velocity, arcade);
        };
    }

    public static void launchPractice(Player player, ThrowMechanicImpl mechanic, Location basket, BoundingBox area,
                                      BooleanSupplier active, Consumer<Boolean> result) {
        double yaw = Math.toRadians(mechanic.effectiveSkills().releaseSpreadDegrees())
                * mechanic.spreadMultiplier() * (2 * Math.random() - 1);
        DiscTrajectory trajectory = practiceTrajectory(player, mechanic, basket, area, yaw);
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        var disc = mechanic.getDisc();
        ItemDisplay display = BukkitCompatibility.spawn(trajectory.location(), ItemDisplay.class, item -> {
            item.setItemStack(DiscItems.discItem(disc.getType(), disc.getName(), player, true));
            item.setBillboard(Display.Billboard.CENTER);
            item.setPersistent(false);
        });
        var flight = new BukkitRunnable() {
            private BukkitTask task;

            @Override public void run() {
                if (!active.getAsBoolean()) { finish(false); return; }
                DiscTrajectory.Step step;
                try {
                    step = trajectory.advance();
                    Location next = step.location();
                    display.teleport(next);
                    next.getWorld().spawnParticle(Particle.END_ROD, next, 1, 0, 0, 0, 0);
                } catch (RuntimeException failure) {
                    plugin.getLogger().log(java.util.logging.Level.WARNING, "Practice flight failed", failure);
                    finish(false);
                    return;
                }
                if (step.finished()) finish(step.goal());
            }

            private void finish(boolean goal) {
                DiscFlightManager.completeFlight(task, plugin, 0);
                result.accept(goal);
            }
        };
        flight.task = flight.runTaskTimer(plugin, 1, 1);
        DiscFlightManager.registerFlight(flight.task, player.getUniqueId(), null, display);
    }

    public static double ratedLaunchSpeed(DiscImpl disc, double baseSpeed) {
        int baseline = DiscModel.starter(disc.getType()).speed();
        return baseSpeed * Math.max(.1, 1 + .06 * (disc.getSpeed() - baseline));
    }

    public static void start(DiscImpl disc, Player player, ItemDisplay display, Vector initial, int maxTicks, String technique, BlockFace facing) {
        RoundImpl round;
        try { round = activeShot(player); }
        catch (IllegalStateException exception) { display.remove(); throw exception; }
        var manager = NDGManager.getInstance();
        var physics = manager.getThrowTechniqueRegistry().get(technique);
        var skills = round.play().effectiveSkills(manager.getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId()).getSkills());
        double drag = skills.flightDrag();
        double turn = skills.turnMultiplier(), fade = skills.fadeMultiplier(technique);
        var trajectory = new DiscTrajectory(display.getLocation(), initial, maxTicks, collision(round, false, null),
                (velocity, tick) -> physics.applyPhysics(disc, velocity, tick, maxTicks, facing, drag, turn, fade),
                new GroundPhysics(false, 0, 0));
        start(disc, player, display, technique, round, trajectory, Stance.NORMAL);
    }

    private static RoundImpl activeShot(Player player) {
        if (DiscFlightResolver.findActiveRound(player) instanceof RoundImpl round && round.play().shot() != null)
            return round;
        throw new IllegalStateException("No active shot");
    }

    private static void start(DiscImpl disc, Player player, ItemDisplay display, String technique,
                              RoundImpl round, DiscTrajectory trajectory, Stance stance) {
        if (round.play().courseEvents().current() == CourseEvent.NIGHT_GOLF) {
            display.setBrightness(new Display.Brightness(15, 15));
            display.setGlowColorOverride(Color.AQUA);
            display.setGlowing(true);
        }
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        var shot = round.play().shot();
        BukkitTask[] taskRef = new BukkitTask[1];
        BukkitRunnable flight = new BukkitRunnable() {
            public void run() {
                if (round.isOver() || round.play().shot() != shot || !player.isOnline()) {
                    round.play().spectating().abortFlight();
                    finish();
                    return;
                }
                try {
                    DiscTrajectory.Step step = trajectory.advance();
                    Location next = step.location();
                    display.teleport(next);
                    round.play().spectating().flightFrame(next);
                    display.getWorld().spawnParticle(Particle.END_ROD, next, 1, 0, 0, 0, 0);
                    ArcadeDisc ability = trajectory.arcadeEffect();
                    if (ability != null) playArcadeEffect(player, next, ability);
                    playContactEffect(player, step);
                    if (step.finished()) {
                        finish();
                        round.play().resolve(player, step, disc.getType(), technique, stance);
                    }
                } catch (RuntimeException exception) {
                    finish();
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Flight failed in " + round.getId(), exception);
                    NDGManager.getInstance().getRoundHandler().cancelRound(round);
                }
            }
            private void finish() { DiscFlightManager.completeFlight(taskRef[0], plugin, 0); }
        };
        BukkitTask task = flight.runTaskTimer(plugin, 1, 1);
        taskRef[0] = task;
        DiscFlightManager.registerFlight(task, player.getUniqueId(), round, display);
        round.play().spectating().beginFlight(player, display);
    }

    private static void playArcadeEffect(Player player, Location at, ArcadeDisc disc) {
        World world = at.getWorld();
        switch (disc) {
            case PHANTOM -> {
                world.spawnParticle(Particle.PORTAL, at, 20, .28, .26, .28, .15);
                world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, .45f, 1.5f);
                player.sendMessage(ChatColor.LIGHT_PURPLE + "Phantom phased through an obstacle.");
            }
            case ROCKET -> {
                world.spawnParticle(Particle.FLAME, at, 16, .20, .20, .20, .075);
                world.playSound(at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, .6f, 1.2f);
                player.sendMessage(ChatColor.GOLD + "Rocket boosted in flight.");
            }
            case STICKY -> {
                world.spawnParticle(BukkitCompatibility.HAPPY_VILLAGER, at, 12, .20, .10, .20, 0);
                world.playSound(at, Sound.BLOCK_SLIME_BLOCK_HIT, SoundCategory.PLAYERS, .7f, 1.2f);
                player.sendMessage(ChatColor.GREEN + "Sticky stopped on contact.");
            }
        }
    }

    private static void playContactEffect(Player player, DiscTrajectory.Step step) {
        Location location = step.location();
        World world = location.getWorld();
        switch (step.impact()) {
            case SKIP -> {
                world.spawnParticle(Particle.CRIT, location, 7, .13, .06, .13, .025);
                world.playSound(location, Sound.BLOCK_STONE_HIT, SoundCategory.PLAYERS, .5f, 1.6f);
                player.sendMessage(ChatColor.GRAY + "Ground skip");
            }
            case SLIDE -> world.spawnParticle(Particle.CLOUD, location, 4, .1, .035, .1, .015);
            case TREE_KICK -> {
                world.spawnParticle(Particle.CRIT, location, 9, .12, .12, .12, .025);
                world.playSound(location, Sound.BLOCK_WOOD_HIT, SoundCategory.PLAYERS, .6f, 1.35f);
                player.sendMessage(ChatColor.GRAY + "Tree kick");
            }
            case ROLL -> {
                world.spawnParticle(Particle.CLOUD, location, 7, .14, .04, .14, .02);
                world.playSound(location, Sound.BLOCK_GRASS_STEP, SoundCategory.PLAYERS, .65f, 1.5f);
                player.sendMessage(ChatColor.GRAY + "Disc is rolling");
            }
            case NONE -> {}
        }
    }
}
