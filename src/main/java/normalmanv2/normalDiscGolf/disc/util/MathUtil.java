package normalmanv2.normalDiscGolf.disc.util;

import normalmanv2.normalDiscGolf.disc.Disc;
import normalmanv2.normalDiscGolf.technique.ThrowTechnique;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class MathUtil {


    public static boolean detectCollision(World world, Location startLocation, Vector direction) {
        double maxDistance = direction.length();
        RayTraceResult result = world.rayTraceBlocks(startLocation, startLocation.clone().add(direction.normalize()).toVector(), maxDistance, FluidCollisionMode.ALWAYS, true);
        return result != null && result.getHitBlock() != null && result.getHitBlock().getType().isSolid();
    }

    public static void adjustFlightPath(Vector currentVelocity, int tickCount, int maxTicks, Disc disc, ThrowTechnique technique) {

        double flightPhase = (double) tickCount / (double) maxTicks;

        double turn = disc.getTurn();
        double fade = disc.getFade();
        double gravityFactor = (double) disc.getGlide() / 200;
        double liftFactor = gravityFactor + (Math.sin(tickCount * 0.1) * 0.01);

        double straightPhaseEnd = 0.2;
        double turnPhaseEnd = 0.3;
        double fadePhaseStart = 0.5;

        currentVelocity.multiply(0.98);

        switch (technique) {

            case BACKHAND -> {

                if (flightPhase <= straightPhaseEnd){
                    return;
                }

                if (Math.random() <= 0.2) {
                    currentVelocity.setY(currentVelocity.getY() + liftFactor);
                }
                currentVelocity.setY(currentVelocity.getY() - gravityFactor);

                if (flightPhase <= turnPhaseEnd) {
                    double turnFactor = (turnPhaseEnd - flightPhase) / turnPhaseEnd;
                    double turnAdjustment = turn * turnFactor * 0.2;
                    currentVelocity.setX(currentVelocity.getX() - turnAdjustment);
                }

                if (flightPhase >= fadePhaseStart) {
                    double fadeFactor = Math.pow((flightPhase - fadePhaseStart) / (1 - fadePhaseStart), 3);
                    double fadeAdjustment = fade * fadeFactor * 0.15;
                    currentVelocity.setX(currentVelocity.getX() + fadeAdjustment);
                }
            }

            case FOREHAND -> {

            }

        }

        currentVelocity.setZ(currentVelocity.getZ() + (Math.sin(tickCount * 0.1) * 0.01));
    }
}
