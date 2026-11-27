package normalmanv2.normalDiscGolf.impl.course.obstacle.generator;

import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.grid.WoodlandHole.Landmark;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.type.Leaves;

final class WoodlandLandmarks {
    private WoodlandLandmarks() {}

    static void place(World world, CourseGrid grid, Landmark landmark) {
        int x = landmark.position().x(), z = landmark.position().z();
        int y = grid.surfaceHeight(x, z) + 1;
        switch (landmark.type()) {
            case OLD_GROWTH_PINE -> pine(world, grid, x, y, z, landmark.height());
            case MOSSY_BOULDER -> boulder(world, grid, x, y, z, landmark.height());
            case RUINED_GATEPOST -> gatepost(world, grid, x, y, z, landmark.height());
        }
    }

    private static void pine(World world, CourseGrid grid, int x, int y, int z, int height) {
        for (int dy = 0; dy < height; dy++) {
            put(world, x, y + dy, z, Material.SPRUCE_LOG);
            if (dy < 3) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) == 1)
                    column(world, grid, x + dx, y + dy, z + dz, Material.SPRUCE_WOOD);
            }
        }
        for (int dy = height - 6; dy <= height; dy++) {
            int radius = switch (height - dy) {
                case 6, 4 -> 3;
                case 5, 3, 2 -> 2;
                case 1 -> 1;
                default -> 0;
            };
            for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius || dx == 0 && dz == 0 && dy < height) continue;
                var block = world.getBlockAt(x + dx, y + dy, z + dz);
                block.setType(Material.SPRUCE_LEAVES, false);
                var leaves = (Leaves)block.getBlockData();
                leaves.setPersistent(true);
                block.setBlockData(leaves, false);
            }
        }
    }

    private static void boulder(World world, CourseGrid grid, int x, int y, int z, int height) {
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            double radius = Math.hypot(dx, dz * 1.18);
            if (radius > Landmark.RADIUS) continue;
            int top = y + height - 1 - (int)Math.round(radius * 1.25);
            int surface = grid.surfaceHeight(x + dx, z + dz);
            for (int dy = surface + 1; dy <= Math.max(surface + 1, top); dy++) {
                Material material = dy >= top ? Material.MOSS_BLOCK
                        : Math.floorMod(dx * 5 + dz * 7 + dy, 4) == 0 ? Material.ANDESITE : Material.MOSSY_COBBLESTONE;
                put(world, x + dx, dy, z + dz, material);
            }
        }
    }

    private static void gatepost(World world, CourseGrid grid, int x, int y, int z, int height) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int radius = Math.abs(dx) + Math.abs(dz);
            if (radius <= 2) column(world, grid, x + dx, y + (radius == 2 ? 0 : 1), z + dz, Material.MOSSY_COBBLESTONE);
        }
        for (int dy = 2; dy < height; dy++) {
            put(world, x, y + dy, z, dy % 2 == 0 ? Material.MOSSY_STONE_BRICKS : Material.CRACKED_STONE_BRICKS);
        }
        put(world, x, y + height, z, Material.CHISELED_STONE_BRICKS);
        put(world, x, y + height + 1, z, Material.LANTERN);
    }

    private static void column(World world, CourseGrid grid, int x, int top, int z, Material material) {
        int bottom = grid.surfaceHeight(x, z) + 1;
        for (int y = bottom; y <= Math.max(top, bottom); y++) put(world, x, y, z, material);
    }

    private static void put(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }
}
