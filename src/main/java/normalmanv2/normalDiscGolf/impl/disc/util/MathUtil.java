package normalmanv2.normalDiscGolf.impl.disc.util;

import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.disc.ArcadeFlight;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Block;
import org.bukkit.util.*;

public final class MathUtil {
    private MathUtil() {}
    public enum HitType { BASKET, BLOCK, HAZARD, LIMIT }

    public record FlightHit(Location location, HitType type, Material material, BlockFace face) {
        public FlightHit(Location location, boolean goal) {
            this(location, goal ? HitType.BASKET : HitType.LIMIT, null, null);
        }

        public boolean goal() { return type == HitType.BASKET; }
        public boolean hazard() { return type == HitType.HAZARD; }
    }
    /** Returns the first hit along the actual movement segment, bounded to this hole's basket. */
    public static FlightHit trace(RoundImpl round, Location start, Vector velocity) {
        return trace(round, start, velocity, null);
    }

    public static FlightHit trace(RoundImpl round, Location start, Vector velocity, ArcadeFlight arcade) {
        double length = velocity.length();
        if (length < 0.000001 || !Double.isFinite(length)) return null;
        Vector direction = velocity.clone().normalize();
        World world = start.getWorld();
        RayTraceResult block = firstSolid(start, direction, length, arcade == null ? null : arcade.phasedBlock());
        RayTraceResult basket = round.getCourse().basketBounds(world, round.getCurrentHole()).rayTrace(start.toVector(), direction, length);
        FlightHit hit = nearestHit(round, start, direction, length, block, basket);
        if (arcade != null && !round.getCourse().isCustom() && arcade.canPhase() && hit != null && hit.type() == HitType.BLOCK
                && hit.face() != null && hit.face() != BlockFace.UP && hit.face() != BlockFace.DOWN) {
            Block obstacle = block.getHitBlock();
            if (obstacle.getY() > round.getCourse().grid().surfaceHeight(obstacle.getX(), obstacle.getZ())) {
                arcade.phase(obstacle);
                return nearestHit(round, start, direction, length, firstSolid(start, direction, length, obstacle), basket);
            }
        }
        return hit;
    }

    private static RayTraceResult firstSolid(Location start, Vector direction, double length, Block ignored) {
        World world = start.getWorld();
        RayTraceResult block = world.rayTraceBlocks(start, direction, length, FluidCollisionMode.NEVER, true);
        if (ignored == null || block == null || !ignored.equals(block.getHitBlock())) return block;
        double exit = Math.min(exitDistance(start.getX(), direction.getX(), ignored.getX()),
                Math.min(exitDistance(start.getY(), direction.getY(), ignored.getY()),
                        exitDistance(start.getZ(), direction.getZ(), ignored.getZ())));
        // Start just after the ignored voxel, preserving collisions along the remaining sweep.
        double offset = Math.max(0, exit) + .00001;
        return offset >= length ? null : world.rayTraceBlocks(start.clone().add(direction.clone().multiply(offset)),
                direction, length - offset, FluidCollisionMode.NEVER, true);
    }

    private static double exitDistance(double position, double direction, int block) {
        if (Math.abs(direction) < 1e-9) return Double.POSITIVE_INFINITY;
        return ((direction > 0 ? block + 1 : block) - position) / direction;
    }

    private static FlightHit nearestHit(RoundImpl round, Location start, Vector direction, double length,
                                        RayTraceResult block, RayTraceResult basket) {
        World world = start.getWorld();
        double blockDistance = block == null ? Double.MAX_VALUE : block.getHitPosition().distance(start.toVector());
        double basketDistance = basket == null ? Double.MAX_VALUE : basket.getHitPosition().distance(start.toVector());
        // The solid ray ignores passable blocks, including liquids.
        double stop = Math.min(length, Math.min(blockDistance, basketDistance));
        int samples = Math.max(1, (int) Math.ceil(stop / .25));
        for (int i = 0; i <= samples; i++) {
            Location sample = start.clone().add(direction.clone().multiply(stop * i / samples));
            Material material = sample.getBlock().getType();
            if (material == Material.WATER || material == Material.LAVA)
                return new FlightHit(sample, HitType.HAZARD, material, null);
        }
        if (basket != null && basketDistance <= blockDistance)
            return new FlightHit(basket.getHitPosition().toLocation(world), true);
        if (block != null) {
            var hitBlock = block.getHitBlock();
            var face = block.getHitBlockFace();
            // Leave the disc outside the struck face so the next sweep can depart it.
            Vector offset = face == null ? direction.clone().multiply(-.025) : face.getDirection().multiply(.025);
            Location contact = block.getHitPosition().add(offset).toLocation(world);
            TileTypes tile = round.getCourse().tileAt(hitBlock.getLocation());
            HitType type = tile.isHazard() ? HitType.HAZARD : HitType.BLOCK;
            return new FlightHit(contact, type, hitBlock.getType(), face);
        }
        return null;
    }
    public static boolean detectCollision(World world, Location start, Vector velocity) {
        return velocity.lengthSquared() > .000001 && world.rayTraceBlocks(start, velocity.clone().normalize(), velocity.length(), FluidCollisionMode.ALWAYS, true) != null;
    }
    /** Material alone never identifies a basket. Kept for API compatibility. */
    @Deprecated public static boolean detectGoalCollision(World world, Location start, Vector velocity) { return false; }
}
