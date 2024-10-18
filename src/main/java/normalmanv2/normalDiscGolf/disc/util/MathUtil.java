package normalmanv2.normalDiscGolf.disc.util;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class MathUtil {

    public static boolean detectCollision(World world, Location startLocation, Vector direction) {
        double maxDistance = direction.length();
        RayTraceResult result = world.rayTraceBlocks(startLocation, startLocation.clone().add(direction.normalize()).toVector(), maxDistance, FluidCollisionMode.ALWAYS, true);

        if (result == null) {
            return false;
        }

        return result.getHitBlock() != null && result.getHitBlock().getType().isSolid() || result.getHitBlock().getType() == Material.WATER;
    }


    /*public static void adjustFlightPath(Vector currentVelocity, int tickCount, int maxTicks, Disc disc, OldThrowTechnique technique) {

        double flightPhase = (double) tickCount / (double) maxTicks;

        double turn = disc.getTurn();
        double fade = disc.getFade();
        double gravityFactor = (double) disc.getGlide() / 200;
        double liftFactor = gravityFactor + (Math.sin(tickCount * 0.1) * 0.01);

        // 20% of the flight
        double straightPhaseEnd = 0.2;
        // 40% of the flight
        double turnPhaseEnd = 0.4;
        // 50% of the flight
        double fadePhaseStart = 0.5;

        // Drag simulation
        currentVelocity.multiply(0.98);

        // Diminishing factor of the turn phase
        final double turnFactor = (turnPhaseEnd - flightPhase) / turnPhaseEnd;
        // Exponential increase of the fade phase - scale the last number for increase / decrease of the exponential effect
        final double fadeFactor = Math.pow((flightPhase - fadePhaseStart) / (1 - fadePhaseStart), 3);

        switch (technique) {

            case BACKHAND -> {

                if (flightPhase <= straightPhaseEnd){
                    return;
                }
                // Random lift factor chance
                if (Math.random() <= 0.2) {
                    currentVelocity.setY(currentVelocity.getY() + liftFactor);
                }
                // Gravity simulation
                currentVelocity.setY(currentVelocity.getY() - gravityFactor);

                // When flight phase is at or above 20% it will maintain these adjustments until 40%
                if (flightPhase <= turnPhaseEnd) {
                    // How the third number on the disc effects flight
                    double turnAdjustment = turn * turnFactor * 0.2;
                    currentVelocity.setX(currentVelocity.getX() - turnAdjustment);
                }

                if (flightPhase >= fadePhaseStart) {
                    // How the fourth number on the disc effects flight
                    double fadeAdjustment = fade * fadeFactor * 0.15;
                    currentVelocity.setX(currentVelocity.getX() + fadeAdjustment);
                }
            }

            case FOREHAND -> {
                if (flightPhase <= straightPhaseEnd){
                    return;
                }

                if (Math.random() <= 0.2) {
                    currentVelocity.setY(currentVelocity.getY() + liftFactor);
                }
                currentVelocity.setY(currentVelocity.getY() - gravityFactor);

                if (flightPhase <= turnPhaseEnd) {
                    double turnAdjustment = turn * turnFactor * 0.2;
                    currentVelocity.setX(currentVelocity.getX() + turnAdjustment);
                }

                if (flightPhase >= fadePhaseStart) {
                    double fadeAdjustment = fade * fadeFactor * 0.15;
                    currentVelocity.setX(currentVelocity.getX() - fadeAdjustment);
                }
            }
        }
        currentVelocity.setZ(currentVelocity.getZ() + (Math.sin(tickCount * 0.1) * 0.01));
    }*/
}
