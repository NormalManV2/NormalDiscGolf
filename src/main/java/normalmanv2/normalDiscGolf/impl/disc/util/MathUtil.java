package normalmanv2.normalDiscGolf.impl.disc.util;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class MathUtil {

    public static boolean detectCollision(World world, Location startLocation, Vector velocity) {
        RayTraceResult result = rayTrace(world, startLocation, velocity);
        if (result == null || result.getHitBlock() == null) {
            return false;
        }

        Block hitBlock = result.getHitBlock();
        return hitBlock.getType().isSolid() || hitBlock.isLiquid();
    }

    public static boolean detectGoalCollision(World world, Location startLocation, Vector velocity) {
        RayTraceResult result = rayTrace(world, startLocation, velocity);
        if (result == null || result.getHitBlock() == null) {
            return false;
        }

        Material hitType = result.getHitBlock().getType();
        return hitType == Material.CHAIN || hitType == Material.OAK_SLAB;
    }

    private static RayTraceResult rayTrace(World world, Location startLocation, Vector velocity) {
        double maxDistance = velocity.length();
        if (maxDistance <= 0) {
            return null;
        }

        return world.rayTraceBlocks(
                startLocation,
                velocity.clone().normalize(),
                maxDistance,
                FluidCollisionMode.ALWAYS,
                true);
    }
}
