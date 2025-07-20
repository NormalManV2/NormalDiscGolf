package normalmanv2.normalDiscGolf.impl.disc.util;

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

        return result.getHitBlock() != null && result.getHitBlock().getType().isSolid() || result.getHitBlock().isLiquid();
    }

    public static boolean detectGoalCollision(World world, Location startLocation, Vector direction) {
        double maxDistance = direction.length();
        RayTraceResult result = world.rayTraceBlocks(startLocation, startLocation.clone().add(direction.normalize()).toVector(), maxDistance, FluidCollisionMode.ALWAYS, true);
        if (result == null) {
            return false;
        }
        return result.getHitBlock() != null && result.getHitBlock().getType() == Material.CHAIN || result.getHitBlock().getType() == Material.OAK_SLAB;
    }

}
