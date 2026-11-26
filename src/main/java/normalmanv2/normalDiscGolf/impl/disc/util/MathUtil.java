package normalmanv2.normalDiscGolf.impl.disc.util;

import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.*;
import org.bukkit.util.*;

public final class MathUtil {
    private MathUtil() {}
    public record FlightHit(Location location, boolean goal) {}
    /** Returns the first hit along the actual movement segment, bounded to this hole's basket. */
    public static FlightHit trace(RoundImpl round, Location start, Vector velocity) {
        double length = velocity.length();
        if (length < 0.000001 || !Double.isFinite(length)) return null;
        Vector direction = velocity.clone().normalize();
        World world = start.getWorld();
        RayTraceResult block = world.rayTraceBlocks(start, direction, length, FluidCollisionMode.NEVER, true);
        RayTraceResult basket = round.getCourse().basketBounds(world, round.getCurrentHole()).rayTrace(start.toVector(), direction, length);
        double blockDistance = block == null ? Double.MAX_VALUE : block.getHitPosition().distance(start.toVector());
        double basketDistance = basket == null ? Double.MAX_VALUE : basket.getHitPosition().distance(start.toVector());
        // The solid ray ignores passable blocks, including liquids, so check water separately.
        double stop = Math.min(length, Math.min(blockDistance, basketDistance));
        int samples = Math.max(1, (int) Math.ceil(stop / .25));
        for (int i = 0; i <= samples; i++) {
            Location sample = start.clone().add(direction.clone().multiply(stop * i / samples));
            if (sample.getBlock().getType() == Material.WATER) return new FlightHit(sample, false);
        }
        if (basket != null && basketDistance <= blockDistance)
            return new FlightHit(basket.getHitPosition().toLocation(world), true);
        if (block != null) return new FlightHit(block.getHitPosition().subtract(direction.multiply(.05)).toLocation(world), false);
        return null;
    }
    public static boolean detectCollision(World world, Location start, Vector velocity) {
        return velocity.lengthSquared() > .000001 && world.rayTraceBlocks(start, velocity.clone().normalize(), velocity.length(), FluidCollisionMode.ALWAYS, true) != null;
    }
    /** Material alone never identifies a basket. Kept for API compatibility. */
    @Deprecated public static boolean detectGoalCollision(World world, Location start, Vector velocity) { return false; }
}
