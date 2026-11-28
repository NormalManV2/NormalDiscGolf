package normalmanv2.normalDiscGolf.impl.course.obstacle.generator;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseTerrain;
import normalmanv2.normalDiscGolf.impl.course.grid.ParklandHole;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import org.bukkit.*;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.*;
import org.bukkit.util.Vector;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;

/** Decorates outside guaranteed open lanes; tees and baskets have exact, shared anchors. */
public final class ObstacleGenerator {
    private static final Method ASYNC_CHUNK_LOAD = asyncChunkMethod();
    private static final int CHUNKS_AHEAD = 24;
    private final CourseGrid grid;
    public ObstacleGenerator(CourseGrid grid, Division division) { this.grid = grid; }
    public void generateObstacles(World world) {
        for (int x = 0; x < grid.getWidth(); x++) for (int z = 0; z < grid.getDepth(); z++) decorate(world, x, z);
        for (int hole = 0; hole < grid.getNumHoles(); hole++) finishHole(world, hole);
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
            final Map<Integer, CompletableFuture<?>> chunks = new HashMap<>();
            int index;
            int requested;
            int hole;
            public void run() {
                try {
                    long deadline = System.nanoTime() + 6_000_000L;
                    while (ASYNC_CHUNK_LOAD != null && requested < Math.min(total, index + CHUNKS_AHEAD)
                            && System.nanoTime() < deadline) {
                        int x = requested / grid.getDepth(), z = requested % grid.getDepth();
                        chunks.put(requested++, requestChunk(world, x, z));
                    }
                    for (int count = 0; count < Math.max(1, batch) && System.nanoTime() < deadline; count++) {
                        if (index == total) {
                            if (hole == grid.getNumHoles()) break;
                            finishHole(world, hole++);
                            continue;
                        }
                        int x = index / grid.getDepth(), z = index % grid.getDepth();
                        if (ASYNC_CHUNK_LOAD != null) {
                            var chunk = chunks.get(index);
                            if (chunk == null || !chunk.isDone()) break;
                            chunk.join();
                            // Keep the chunk ready for tee placement and lane validation at the end.
                            if (!world.isChunkLoaded(x, z)) {
                                chunks.put(index, requestChunk(world, x, z));
                                break;
                            }
                            world.addPluginChunkTicket(x, z, plugin);
                            chunks.remove(index);
                        }
                        decorate(world, x, z);
                        index++;
                    }
                    if (progress != null) progress.accept(index + hole, total + grid.getNumHoles());
                    if (index == total && hole == grid.getNumHoles()) {
                        cancel();
                        world.removePluginChunkTickets(plugin);
                        if (complete != null) complete.run();
                    }
                } catch (RuntimeException exception) {
                    if (!isCancelled()) cancel();
                    world.removePluginChunkTickets(plugin);
                    if (failure != null) failure.accept(exception); else throw exception;
                }
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    private static Method asyncChunkMethod() {
        try { return World.class.getMethod("getChunkAtAsync", int.class, int.class, boolean.class); }
        catch (NoSuchMethodException unavailable) { return null; }
    }

    private static CompletableFuture<?> requestChunk(World world, int x, int z) {
        if (world.isChunkLoaded(x, z)) return CompletableFuture.completedFuture(null);
        try { return (CompletableFuture<?>) ASYNC_CHUNK_LOAD.invoke(world, x, z, true); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Could not load course chunk", failure); }
    }
    private void decorate(World world, int tx, int tz) {
        if (grid.isThemed()) {
            decorateThemed(world, tx, tz);
            return;
        }
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

    private void decorateThemed(World world, int tx, int tz) {
        double density = grid.terrainSettings().obstacleDensity();
        if (density == 0) return;
        var random = new Random(grid.seed() ^ tx * 341873128712L ^ tz * 132897987541L);
        for (int i = 0; i < 5; i++) {
            int x = tx * 16 + 2 + random.nextInt(12), z = tz * 16 + 2 + random.nextInt(12);
            if (grid.nearLandmark(x, z, 3)) continue;
            if (random.nextDouble() < density * .58 && protectedFootprint(world, x, z, 3)) {
                feature(world, x, z, random, false);
            } else if (random.nextDouble() < density && protectedFootprint(world, x, z, 0)) {
                int y = grid.surfaceHeight(x, z);
                Material ground = world.getBlockAt(x, y, z).getType();
                Material plant = switch (ground) {
                    case GRASS_BLOCK, PODZOL -> grid.terrain() == CourseTerrain.WOODLAND ? Material.FERN : Material.OXEYE_DAISY;
                    case CRIMSON_NYLIUM -> Material.CRIMSON_ROOTS;
                    case SAND, RED_SAND -> Material.DEAD_BUSH;
                    default -> null;
                };
                if (plant != null) world.getBlockAt(x, y + 1, z).setType(plant, false);
            }
        }
    }

    private boolean protectedFootprint(World world, int x, int z, int radius) {
        int height = grid.surfaceHeight(x, z);
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (grid.protectedLane(x + dx, z + dz) || grid.surfaceType(x + dx, z + dz).isHazard()) return false;
        }
        return world.getBlockAt(x, height, z).getType().isSolid() && world.getBlockAt(x, height + 1, z).isEmpty();
    }

    private void feature(World world, int x, int z, Random random, boolean guard) {
        int y = grid.surfaceHeight(x, z) + 1;
        switch (grid.terrain()) {
            case WOODLAND -> {
                boolean spruce = random.nextBoolean();
                tree(world, x, y, z, spruce ? Material.SPRUCE_LOG : Material.OAK_LOG,
                        spruce ? Material.SPRUCE_LEAVES : Material.OAK_LEAVES, (guard ? 7 : 4) + random.nextInt(3));
            }
            case MOUNTAIN -> spire(world, x, y, z, Material.STONE, Material.SNOW_BLOCK, (guard ? 6 : 2) + random.nextInt(4));
            case DESERT -> spire(world, x, y, z, Material.CUT_SANDSTONE, Material.RED_TERRACOTTA,
                    (guard ? 6 : 2) + random.nextInt(4));
            case ISLAND -> tree(world, x, y, z, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES, 6 + random.nextInt(3));
            case NETHER -> spire(world, x, y, z, random.nextBoolean() ? Material.BASALT : Material.BLACKSTONE,
                    Material.SHROOMLIGHT, (guard ? 6 : 3) + random.nextInt(4));
            case SKY -> spire(world, x, y, z, Material.CALCITE, Material.MOSS_BLOCK, (guard ? 6 : 2) + random.nextInt(4));
            case PARKLAND -> { }
        }
    }

    private void spire(World world, int x, int y, int z, Material base, Material cap, int height) {
        for (int dy = 0; dy < height; dy++)
            world.getBlockAt(x, y + dy, z).setType(dy == height - 1 ? cap : base, false);
        for (int[] side : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            int sx = x + side[0], sz = z + side[1];
            var shoulder = world.getBlockAt(sx, grid.surfaceHeight(sx, sz) + 1, sz);
            if (shoulder.isEmpty()) shoulder.setType(base, false);
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
    private void finishHole(World world, int hole) {
        var tee = grid.getTeePoints().get(hole);
        var pin = grid.getHolePoints().get(hole);
        int ty = grid.surfaceHeight(tee.x(), tee.z()), py = grid.surfaceHeight(pin.x(), pin.z());
        if (grid.isThemed() && grid.terrainSettings().obstacleDensity() > 0) {
            var random = new Random(grid.seed() + hole);
            for (var guard : grid.guards(hole)) if (!grid.nearLandmark(guard.x(), guard.z(), 3)
                    && protectedFootprint(world, guard.x(), guard.z(), 3))
                feature(world, guard.x(), guard.z(), random, true);
        }
        for (var landmark : grid.woodlandLandmarks(hole)) WoodlandLandmarks.place(world, grid, landmark);

        String hint = grid.holeHint(hole);
        Material teeBorder = hint == null ? Material.STONE_BRICKS : switch (grid.terrain()) {
            case MOUNTAIN -> Material.POLISHED_ANDESITE;
            case DESERT -> Material.CHISELED_RED_SANDSTONE;
            case ISLAND -> Material.STRIPPED_JUNGLE_WOOD;
            case NETHER -> Material.POLISHED_BLACKSTONE_BRICKS;
            case SKY -> Material.POLISHED_TUFF;
            default -> Material.STRIPPED_SPRUCE_WOOD;
        };
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            Material tile = Math.abs(dx) == 2 || Math.abs(dz) == 2 ? teeBorder : Material.SMOOTH_STONE;
            world.getBlockAt(tee.x() + dx, ty, tee.z() + dz).setType(tile, false);
            for (int dy = 1; dy <= 3; dy++) world.getBlockAt(tee.x() + dx, ty + dy, tee.z() + dz).setType(Material.AIR, false);
        }
        world.getBlockAt(pin.x(), py + 1, pin.z()).setType(Material.IRON_BARS, false);
        world.getBlockAt(pin.x(), py + 2, pin.z()).setType(Material.CHAIN, false);
        world.getBlockAt(pin.x(), py + 3, pin.z()).setType(Material.YELLOW_CONCRETE, false);
        for (int[] arm : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
            world.getBlockAt(pin.x() + arm[0], py + 1, pin.z() + arm[1]).setType(Material.IRON_TRAPDOOR, false);
            world.getBlockAt(pin.x() + arm[0], py + 2, pin.z() + arm[1]).setType(Material.CHAIN, false);
            world.getBlockAt(pin.x() + arm[0], py + 3, pin.z() + arm[1]).setType(Material.YELLOW_STAINED_GLASS, false);
        }

        placeTeeSign(world, hole, tee, pin, ty, hint);
        label(world, new Location(world, pin.x() + .5, py + 4.3, pin.z() + .5), "§e⚑ " + (hole + 1));
        // Validate the full centerline at standing and disc height after decoration.
        validateLane(world, hole, grid.centerline(hole));
        validateLane(world, hole, grid.alternateLane(hole));
    }

    private void placeTeeSign(World world, int hole, CourseGrid.GridPoint tee, CourseGrid.GridPoint pin, int ty, String hint) {
        var opening = grid.openingTarget(hole);
        var forward = new Vector(opening.x() - tee.x(), 0, opening.z() - tee.z()).normalize();
        long distance = Math.round(Math.hypot(pin.x() - tee.x(), pin.z() - tee.z()));
        String details = "§fPar " + grid.getHolePars().get(hole) + "  §7•  " + distance + " blocks";
        if (hint == null) {
            var teeSign = new Location(world, tee.x() + .5, ty + 2.5, tee.z() + .5).subtract(forward.multiply(3.5));
            label(world, teeSign, "§2§l" + grid.holeName(hole) + " • " + (hole + 1) + "\n" + details);
            return;
        }

        var sign = new Location(world, tee.x() + .5, ty + 2, tee.z() + .5)
                .add(forward.clone().multiply(5)).add(new Vector(-forward.getZ(), 0, forward.getX()).multiply(3.5));
        world.spawn(sign, TextDisplay.class, display -> {
            String tipColor = switch (grid.terrain()) {
                case DESERT -> "§e";
                case MOUNTAIN, ISLAND, SKY -> "§b";
                case NETHER -> "§6";
                default -> "§a";
            };
            display.setText("§6§lHOLE " + (hole + 1) + "  §r§f§l" + grid.holeName(hole) + "\n" + details
                    + "\n" + tipColor + hint);
            display.setLineWidth(230);
            display.setBillboard(Display.Billboard.VERTICAL);
            display.setBackgroundColor(switch (grid.terrain()) {
                case MOUNTAIN -> Color.fromARGB(224, 30, 43, 54);
                case DESERT -> Color.fromARGB(224, 56, 31, 15);
                case ISLAND -> Color.fromARGB(224, 11, 51, 55);
                case NETHER -> Color.fromARGB(224, 52, 19, 28);
                case SKY -> Color.fromARGB(224, 20, 31, 53);
                default -> Color.fromARGB(224, 17, 33, 24);
            });
            display.setShadowed(true);
            display.setBrightness(new Display.Brightness(14, 14));
            display.setViewRange(.55f);
        });
    }

    private void validateLane(World world, int hole, List<CourseGrid.GridPoint> route) {
        var pin = grid.getHolePoints().get(hole);
        for (int i = 1; i < route.size(); i++) {
            var a = route.get(i - 1);
            var b = route.get(i);
            int steps = Math.max(1, (int)Math.ceil(Math.hypot(b.x() - a.x(), b.z() - a.z()) / 3));
            for (int step = 0; step <= steps; step++) {
                int x = (int)Math.round(a.x() + (b.x() - a.x()) * (step / (double)steps));
                int z = (int)Math.round(a.z() + (b.z() - a.z()) * (step / (double)steps));
                if (grid.surfaceType(x, z).isHazard() || Math.abs(x - pin.x()) + Math.abs(z - pin.z()) <= 1) continue;
                int y = grid.surfaceHeight(x, z);
                if (!world.getBlockAt(x, y + 1, z).isPassable() || !world.getBlockAt(x, y + 2, z).isPassable())
                    throw new IllegalStateException("Blocked fairway on hole " + (hole + 1) + " at " + x + ", " + z);
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
