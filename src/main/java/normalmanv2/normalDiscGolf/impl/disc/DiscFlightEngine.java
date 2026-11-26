package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil;
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.resourcepack.DiscItems;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.scheduler.*;
import org.bukkit.util.Vector;

public final class DiscFlightEngine {
    private DiscFlightEngine() {}

    static void launch(DiscImpl disc, Player player, PlayerSkills skills, double baseSpeed, double power,
                       int maxTicks, String technique, BlockFace facing) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        double yawError = Math.toRadians(skills.releaseSpreadDegrees()) * (2 * Math.random() - 1);
        direction.rotateAroundY(yawError);
        Location origin = eye.add(direction.clone().multiply(.35));
        ItemDisplay display = player.getWorld().spawn(origin, ItemDisplay.class, item -> {
            item.setItemStack(DiscItems.discItem(disc.getType(), disc.getName(), player, true));
            item.setBillboard(Display.Billboard.CENTER);
            item.setCustomName(ChatColor.translateAlternateColorCodes('&', disc.getName()));
            item.setCustomNameVisible(true);
            item.setPersistent(false);
        });
        Vector velocity = direction.multiply(ratedLaunchSpeed(disc, baseSpeed) * power * skills.launchSpeedMultiplier());
        disc.applyDiscPhysics(player, display, velocity, maxTicks, technique, facing);
    }

    public static double ratedLaunchSpeed(DiscImpl disc, double baseSpeed) {
        int baseline = DiscModel.starter(disc.getType()).speed();
        return baseSpeed * Math.max(.1, 1 + .06 * (disc.getSpeed() - baseline));
    }

    public static void start(DiscImpl disc, Player player, ItemDisplay display, Vector initial, int maxTicks, String technique, BlockFace facing) {
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        var active = DiscFlightResolver.findActiveRound(player);
        if (!(active instanceof RoundImpl round) || round.play().shot() == null) { display.remove(); throw new IllegalStateException("No active shot"); }
        var shot = round.play().shot();
        var manager = NDGManager.getInstance();
        var physics = manager.getThrowTechniqueRegistry().get(technique);
        var skills = manager.getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId()).getSkills();
        double drag = skills.flightDrag(), fadeMultiplier = skills.fadeMultiplier(technique);
        BukkitTask[] taskRef = new BukkitTask[1];
        BukkitRunnable flight = new BukkitRunnable() {
            Vector velocity = initial.clone();
            int tick;
            public void run() {
                if (round.isOver() || round.play().shot() != shot || !player.isOnline()) { finish(); return; }
                try {
                    Location from = display.getLocation();
                    var hit = MathUtil.trace(round, from, velocity);
                    Location next = hit == null ? from.clone().add(velocity) : hit.location();
                    display.teleport(next);
                    display.getWorld().spawnParticle(Particle.END_ROD, next, 1, 0, 0, 0, 0);
                    if (hit != null || tick++ >= maxTicks) {
                        finish();
                        round.play().resolve(player, next, hit != null && hit.goal(), disc.getType(), technique);
                        return;
                    }
                    physics.applyPhysics(disc, velocity, tick, maxTicks, facing, drag, fadeMultiplier);
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
    }
}
