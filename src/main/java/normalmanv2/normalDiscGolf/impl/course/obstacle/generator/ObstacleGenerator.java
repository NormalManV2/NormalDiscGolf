package normalmanv2.normalDiscGolf.impl.course.obstacle.generator;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
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
    private final Random random = new Random(2026);
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
        if (type == TileTypes.WATER || type == TileTypes.SAND) return;
        for (int i = 0; i < 10; i++) {
            int x = tx * 16 + 3 + random.nextInt(10), z = tz * 16 + 3 + random.nextInt(10);
            int y = grid.surfaceHeight(x, z) + 1;
            if (grid.protectedLane(x, z) || !world.getBlockAt(x, y, z).isEmpty()) continue;
            if (i == 0) tree(world, x, y, z, random.nextBoolean());
            else if (i == 1) {
                world.getBlockAt(x, y, z).setType(Material.MOSSY_COBBLESTONE, false);
                world.getBlockAt(x + 1, y, z).setType(Material.MOSS_BLOCK, false);
            } else world.getBlockAt(x, y, z).setType(i % 3 == 0 ? Material.FERN : i % 3 == 1 ? Material.SHORT_GRASS : Material.OXEYE_DAISY, false);
        }
    }
    private void tree(World world, int x, int y, int z, boolean birch) {
        int height = birch ? 6 : 4;
        for (int dy = height - 2; dy <= height + 1; dy++)
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                if (dx * dx + dz * dz <= (dy == height + 1 ? 2 : 6) && world.getBlockAt(x + dx, y + dy, z + dz).isEmpty()) {
                    var block = world.getBlockAt(x + dx, y + dy, z + dz);
                    block.setType(birch ? Material.BIRCH_LEAVES : Material.OAK_LEAVES, false);
                    var leaves = (org.bukkit.block.data.type.Leaves)block.getBlockData();
                    leaves.setPersistent(true); block.setBlockData(leaves, false);
                }
        for (int dy = 0; dy < height; dy++) world.getBlockAt(x, y + dy, z).setType(birch ? Material.BIRCH_LOG : Material.OAK_LOG, false);
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
            label(world, new Location(world, tee.x()+.5, ty+2.5, tee.z()+3.5),
                    "§2§lPARKLAND • " + (hole+1) + "\n§fPar " + grid.getHolePars().get(hole) + "  §7•  " + Math.round(Math.hypot(pin.x()-tee.x(), pin.z()-tee.z())) + " blocks");
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
