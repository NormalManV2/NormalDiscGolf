package normalmanv2.normalDiscGolf.impl.course.obstacle.generator;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.grid.ParklandHole;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import org.bukkit.*;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.*;
import java.util.*;
import java.util.function.*;

/** Decorates outside guaranteed open lanes; tees and baskets have exact, shared anchors. */
public final class ObstacleGenerator {
    private final CourseGrid grid;
    public ObstacleGenerator(CourseGrid grid, Division division) { this.grid = grid; }
    public void generateObstacles(World world) {
        for (int x = 0; x < grid.getWidth(); x++) for (int z = 0; z < grid.getDepth(); z++) decorate(world, x, z);
        finish(world);
    }
    public BukkitTask generateObstaclesIncrementally(Plugin plugin, World world, int batch, Runnable complete) {
        return generateObstaclesIncrementally(plugin, world, batch, null, complete, null);
    }
    public BukkitTask generateObstaclesIncrementally(Plugin plugin, World world, int batch, BiConsumer<Integer,Integer> progress, Runnable complete) {
        return generateObstaclesIncrementally(plugin, world, batch, progress, complete, null);
    }
    public BukkitTask generateObstaclesIncrementally(Plugin plugin, World world, int batch, BiConsumer<Integer,Integer> progress, Runnable complete, Consumer<RuntimeException> failure) {
        int total = grid.getWidth() * grid.getDepth();
        return new BukkitRunnable() {
            int index;
            public void run() {
                try {
                    long deadline = System.nanoTime() + 6_000_000L;
                    for (int count = 0; count < Math.max(1, batch) && index < total && (count == 0 || System.nanoTime() < deadline); count++, index++)
                        decorate(world, index / grid.getDepth(), index % grid.getDepth());
                    if (progress != null) progress.accept(index, total);
                    if (index == total) { cancel(); finish(world); if (complete != null) complete.run(); }
                } catch (RuntimeException exception) { cancel(); if (failure != null) failure.accept(exception); else throw exception; }
            }
        }.runTaskTimer(plugin, 1, 1);
    }
    private void decorate(World world, int tx, int tz) {
        var type = grid.getTile(tx, tz).getCollapsedState();
        if (type == TileTypes.WATER || type == TileTypes.SAND || type == TileTypes.OUT_OF_BOUNDS) return;
        var landscape = grid.landscapeAt(tx, tz);
        var random = new Random(grid.seed() ^ tx * 341873128712L ^ tz * 132897987541L);
        int features = switch (landscape) {
            case MEADOW -> random.nextInt(3) == 0 ? 1 : 0;
            case GROVE, PINES -> type == TileTypes.FAIRWAY ? 1 : 3;
            default -> 2;
        };
        for (int i = 0; i < 8; i++) {
            int x = tx * 16 + 3 + random.nextInt(10), z = tz * 16 + 3 + random.nextInt(10);
            int y = grid.surfaceHeight(x, z) + 1;
            if (grid.protectedLane(x, z) || !world.getBlockAt(x, y, z).isEmpty()) continue;
            if (i >= features || grid.routeDistance(x, z) < 10) {
                var plant = landscape == ParklandHole.POND
                        ? Material.FERN : i % 2 == 0 ? Material.SHORT_GRASS : Material.OXEYE_DAISY;
                world.getBlockAt(x, y, z).setType(plant, false);
                continue;
            }
            switch (landscape) {
                case GROVE -> tree(world, x, y, z, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 5 + random.nextInt(3));
                case PINES -> tree(world, x, y, z, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES, 6 + random.nextInt(3));
                case QUARRY -> rocks(world, x, z, random);
                case RIDGE, POND, MEADOW -> {
                    if (i == 0) tree(world, x, y, z, Material.OAK_LOG, Material.OAK_LEAVES, 4 + random.nextInt(2));
                    else rocks(world, x, z, random);
                }
            }
        }
    }
    private void rocks(World world, int x, int z, Random random) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if ((dx != 0 || dz != 0) && !random.nextBoolean()) continue;
            int y = grid.surfaceHeight(x + dx, z + dz) + 1;
            var block = world.getBlockAt(x + dx, y, z + dz);
            if (block.isEmpty()) block.setType(random.nextBoolean() ? Material.MOSSY_COBBLESTONE : Material.ANDESITE, false);
            if (dx == 0 && dz == 0 && block.getRelative(0, 1, 0).isEmpty())
                block.getRelative(0, 1, 0).setType(Material.COBBLESTONE_SLAB, false);
        }
    }
    private void tree(World world, int x, int y, int z, Material log, Material foliage, int height) {
        boolean pine = log == Material.SPRUCE_LOG;
        for (int dy = height - (pine ? 4 : 2); dy <= height + 1; dy++)
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                if (dx * dx + dz * dz <= (dy >= height ? (pine ? 0 : 2) : 6)
                        && world.getBlockAt(x + dx, y + dy, z + dz).isEmpty()) {
                    var block = world.getBlockAt(x + dx, y + dy, z + dz);
                    block.setType(foliage, false);
                    var leaves = (org.bukkit.block.data.type.Leaves)block.getBlockData();
                    leaves.setPersistent(true); block.setBlockData(leaves, false);
                }
        for (int dy = 0; dy < height; dy++) world.getBlockAt(x, y + dy, z).setType(log, false);
    }
    private void finish(World world) {
        for (int hole = 0; hole < grid.getNumHoles(); hole++) {
            var tee = grid.getTeePoints().get(hole); var pin = grid.getHolePoints().get(hole);
            int ty = grid.surfaceHeight(tee.x(), tee.z()), py = grid.surfaceHeight(pin.x(), pin.z());
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(tee.x()+dx, ty, tee.z()+dz).setType(Math.abs(dx)==2 || Math.abs(dz)==2 ? Material.STONE_BRICKS : Material.SMOOTH_STONE, false);
                for (int dy = 1; dy <= 3; dy++) world.getBlockAt(tee.x()+dx, ty+dy, tee.z()+dz).setType(Material.AIR, false);
            }
            world.getBlockAt(pin.x(), py+1, pin.z()).setType(Material.IRON_BARS, false);
            world.getBlockAt(pin.x(), py+2, pin.z()).setType(Material.CHAIN, false);
            world.getBlockAt(pin.x(), py+3, pin.z()).setType(Material.YELLOW_CONCRETE, false);
            for (int[] arm : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                world.getBlockAt(pin.x()+arm[0], py+1, pin.z()+arm[1]).setType(Material.IRON_TRAPDOOR, false);
                world.getBlockAt(pin.x()+arm[0], py+2, pin.z()+arm[1]).setType(Material.CHAIN, false);
                world.getBlockAt(pin.x()+arm[0], py+3, pin.z()+arm[1]).setType(Material.YELLOW_STAINED_GLASS, false);
            }
            var opening = grid.openingTarget(hole);
            var forward = new org.bukkit.util.Vector(opening.x() - tee.x(), 0, opening.z() - tee.z()).normalize();
            var teeSign = new Location(world, tee.x() + .5, ty + 2.5, tee.z() + .5).subtract(forward.multiply(3.5));
            label(world, teeSign,
                    "§2§l" + grid.holeName(hole) + " • " + (hole+1) + "\n§fPar " + grid.getHolePars().get(hole) + "  §7•  " + Math.round(Math.hypot(pin.x()-tee.x(), pin.z()-tee.z())) + " blocks");
            label(world, new Location(world, pin.x()+.5, py+4.3, pin.z()+.5), "§e⚑ " + (hole+1));
            // Validate the full centerline at standing and disc height after decoration.
            for (var point : grid.getHoleRoutePoints().get(hole)) {
                int x=point.x()+8, z=point.z()+8, y=grid.surfaceHeight(x,z);
                if (!world.getBlockAt(x,y+1,z).isPassable() && !(x==pin.x() && z==pin.z()))
                    throw new IllegalStateException("Blocked fairway on hole " + (hole+1));
            }
        }
    }
    private void label(World world, Location location, String text) {
        world.spawn(location, TextDisplay.class, display -> {
            display.setText(text); display.setBillboard(Display.Billboard.CENTER);
            display.setPersistent(false); display.setSeeThrough(false);
        });
    }
}
