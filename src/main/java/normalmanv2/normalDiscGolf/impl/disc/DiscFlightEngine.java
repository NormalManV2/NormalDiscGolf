package normalmanv2.normalDiscGolf.impl.disc;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.disc.util.MathUtil;
import normalmanv2.normalDiscGolf.impl.manager.flight.DiscFlightManager;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.scheduler.*;
import org.bukkit.util.Vector;

public final class DiscFlightEngine {
    private DiscFlightEngine() {}
    public static void start(DiscImpl disc, Player player, ItemDisplay display, Vector initial, int maxTicks, String technique, BlockFace facing) {
        var plugin = NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class);
        var active = DiscFlightResolver.findActiveRound(player);
        if (!(active instanceof RoundImpl round) || round.play().shot() == null) { display.remove(); throw new IllegalStateException("No active shot"); }
        var shot = round.play().shot();
        var physics = NDGManager.getInstance().getThrowTechniqueRegistry().get(technique);
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
                        round.play().resolve(player, next, hit != null && hit.goal());
                        return;
                    }
                    physics.applyPhysics(disc, velocity, tick, maxTicks, facing);
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
