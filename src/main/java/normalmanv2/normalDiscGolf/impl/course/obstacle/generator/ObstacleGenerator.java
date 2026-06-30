package normalmanv2.normalDiscGolf.impl.course.obstacle.generator;

import com.sk89q.worldedit.math.BlockVector3;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.difficulty.CourseDifficulty;
import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;
import normalmanv2.normalDiscGolf.impl.course.obstacle.WeightedObstacle;
import normalmanv2.normalDiscGolf.impl.course.tile.Tile;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.normal.impl.registry.RegistryImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiConsumer;

public class ObstacleGenerator {

    private final CourseGrid courseGrid;
    private final Random random;
    private final RegistryImpl<String, ObstacleImpl> registry;
    private final Division division;
    private final Map<Division, CourseDifficulty> divisionCourseDifficulty;
    private final List<BoundingBox> placedObstacleBounds = new ArrayList<>();
    private final List<BoundingBox> placedTileBounds = new ArrayList<>();

    public ObstacleGenerator(CourseGrid courseGrid, Division division) {
        this.courseGrid = courseGrid;
        this.random = new Random();
        this.division = division;
        this.registry = NDGManager.getInstance().getObstacleRegistry();
        this.divisionCourseDifficulty = NDGManager.getInstance().getDivisionCourseRegistry().getBackingMap();


        // Populate tile bounds for later obstacle placement validation.
        for (int x = 0; x < courseGrid.getGrid().length; x++) {
            for (int z = 0; z < courseGrid.getGrid()[0].length; z++) {
                Tile tile = courseGrid.getTile(x, z);
                this.placedTileBounds.add(tile.getBoundingBox());
            }
        }
    }

    public void generateObstacles(World world) {
        for (int x = 0; x < courseGrid.getWidth(); x++) {
            for (int z = 0; z < courseGrid.getDepth(); z++) {
                placeObstacle(courseGrid.getTile(x, z), world);
            }
        }
    }

    public void generateObstaclesIncrementally(Plugin plugin, World world, int tilesPerTick, Runnable onComplete) {
        this.generateObstaclesIncrementally(plugin, world, tilesPerTick, null, onComplete);
    }

    public void generateObstaclesIncrementally(
            Plugin plugin,
            World world,
            int tilesPerTick,
            BiConsumer<Integer, Integer> onProgress,
            Runnable onComplete) {
        if (plugin == null) throw new IllegalArgumentException("Plugin cannot be null");
        if (world == null) throw new IllegalArgumentException("World cannot be null");

        final int tilesPerBatch = Math.max(1, tilesPerTick);
        final int totalTiles = courseGrid.getWidth() * courseGrid.getDepth();

        new BukkitRunnable() {
            private int x;
            private int z;
            private int completedTiles;

            @Override
            public void run() {
                int processedTiles = 0;

                while (processedTiles < tilesPerBatch && x < courseGrid.getWidth()) {
                    Tile tile = courseGrid.getTile(x, z);

                    try {
                        if (tile != null) {
                            placeObstacle(tile, world);
                        }
                    } catch (RuntimeException exception) {
                        String message = exception.getMessage() == null
                                ? exception.getClass().getSimpleName()
                                : exception.getMessage();
                        plugin.getLogger().warning("Skipping obstacle tile " + x + "," + z + ": " + message);
                    }

                    processedTiles++;
                    completedTiles++;

                    if (onProgress != null) {
                        onProgress.accept(completedTiles, totalTiles);
                    }

                    z++;

                    if (z >= courseGrid.getDepth()) {
                        z = 0;
                        x++;
                    }
                }

                if (x >= courseGrid.getWidth()) {
                    cancel();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 4L);
    }

    private void placeObstacle(Tile tile, World world) {
        TileTypes tileType = tile.getCollapsedState();

        if (tileType == TileTypes.WATER || tileType == TileTypes.OUT_OF_BOUNDS || tileType == TileTypes.SAND) {
            return;
        }

        int maxObstaclesPerTile = this.calculateMaxObstacles(tile);
        int clusters = Math.max(1, (int) Math.ceil(maxObstaclesPerTile * this.getClusterDensityFactor(tile)));

        // Random cluster centers within a tile.
        List<Location> clusterCenters = new ArrayList<>();
        double tileSize = Constants.DEFAULT_TILE_SIZE;
        CourseGrid.GridPoint tileBase = tile.getGridPoint();

        for (int i = 0; i < clusters; i++) {
            double cx = tileBase.x() + random.nextDouble() * tileSize;
            double cz = tileBase.z() + random.nextDouble() * tileSize;
            clusterCenters.add(new Location(world, cx, tileBase.y(), cz));
        }

        int placed = 0;
        int attempts = 0;

        // Generation attempt loop
        while (placed < maxObstaclesPerTile && attempts++ < maxObstaclesPerTile * Constants.DEFAULT_MAX_OBSTACLE_GENERATION_ATTEMPTS) {
            ObstacleImpl prototype = this.selectObstacle(tile);
            if (prototype == null) throw new RuntimeException("Obstacle is null!");

            Location center = clusterCenters.get(random.nextInt(clusterCenters.size()));

            // Offset from cluster center
            double radius = Constants.DEFAULT_OBSTACLE_CLUSTER_RADIUS;
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius;

            double offsetX = Math.cos(angle) * distance;
            double offsetZ = Math.sin(angle) * distance;

            BlockVector3 size = prototype.getSchematicSize();
            double halfWidth = size.x() / 2.0;
            double halfDepth = size.z() / 2.0;

            // Clamp x/z coordinates to ensure placement within tile bounds.
            double minX = tileBase.x() + halfWidth;
            double maxX = tileBase.x() + tileSize - halfWidth;
            double minZ = tileBase.z() + halfDepth;
            double maxZ = tileBase.z() + tileSize - halfDepth;

            double finalX = this.clamp(center.getX() + offsetX, minX, maxX);
            double finalZ = this.clamp(center.getZ() + offsetZ, minZ, maxZ);

            Location finalLoc = new Location(world, finalX, tileBase.y(), finalZ);
            ObstacleImpl obstacle = prototype.clone();
            obstacle.setLocation(finalLoc);

            // Extra bound validation
            BoundingBox box = obstacle.getBoundingBox();

            // ensure center is within the tile bounds (tile.getBoundingBox contains the obstacle bounds)
            BoundingBox tileBox = tile.getBoundingBox();
            if (!containsTile(tileBox, box)) {
                //NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger().warning("Obstacle is outside of tile bounds!");
                continue;
            }

            // ensure obstacle doesn't overlap previously placed obstacles
            boolean overlapsObstacle = this.placedObstacleBounds.stream().anyMatch(box::overlaps);
            if (overlapsObstacle) {
                //NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger().warning("Obstacle is overlapping another obstacle!");
                continue;
            }

            // Tee/pin objects are allowed to occupy their own tee/pin safety zone.
            if (tileType != TileTypes.TEE && tileType != TileTypes.PIN && !safeFromTeesPins(box)) {
                //NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger().warning("Obstacle is not a safe distance from tee/pin tile");
                continue;
            }

            // shouldPlaceObstacle still checks ground validity etc.
            if (shouldPlaceObstacle(tile, obstacle, world)) {
                obstacle.generate();
                this.placedObstacleBounds.add(box);
                placed++;
                //NormalDiscGolfPlugin.getPlugin(NormalDiscGolfPlugin.class).getLogger().info("Obstacle generated: " + obstacle.getSchematicName());
            }
        }
    }

    private boolean containsTile(BoundingBox tileBox, BoundingBox box) {
        return box.getMinX() >= tileBox.getMinX()
                && box.getMaxX() <= tileBox.getMaxX()
                && box.getMinZ() >= tileBox.getMinZ()
                && box.getMaxZ() <= tileBox.getMaxZ();
    }

    private boolean safeFromTeesPins(BoundingBox box) {
        double TEE_R = 0.5 * Constants.DEFAULT_TILE_SIZE;
        double PIN_R = 0.5 * Constants.DEFAULT_TILE_SIZE;
        for (CourseGrid.GridPoint loc : courseGrid.getTeePoints().values()) {
            BoundingBox tb = new BoundingBox(
                    loc.x() - TEE_R, loc.y() - 4, loc.z() - TEE_R,
                    loc.x() + TEE_R, loc.y() + 16, loc.z() + TEE_R
            );
            if (tb.overlaps(box)) return false;
        }
        for (CourseGrid.GridPoint loc : courseGrid.getHolePoints().values()) {
            BoundingBox pb = new BoundingBox(
                    loc.x() - PIN_R, loc.y() - 4, loc.z() - PIN_R,
                    loc.x() + PIN_R, loc.y() + 16, loc.z() + PIN_R
            );
            if (pb.overlaps(box)) return false;
        }
        return true;
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private CourseDifficulty getCourseDifficulty() {
        CourseDifficulty difficulty = this.divisionCourseDifficulty.get(this.division);
        if (difficulty == null) {
            return CourseDifficulty.MEDIUM;
        }

        return difficulty;
    }

    private int calculateMaxObstacles(Tile tile) {
        double baseFactor = this.getCourseDifficulty().getDensityFactor();

        return switch (tile.getCollapsedState()) {
            case OBSTACLE -> (int) Math.ceil(baseFactor * Constants.DEFAULT_OBSTACLE_TILE_OBSTACLES_LIMIT);
            case FAIRWAY -> (int) Math.ceil(baseFactor * Constants.DEFAULT_FAIRWAY_TILE_OBSTACLES_LIMIT);
            case HEAVY_ROUGH -> (int) Math.ceil(baseFactor * Constants.DEFAULT_HEAVY_ROUGH_TILE_OBSTACLES_LIMIT);
            case LIGHT_ROUGH -> (int) Math.ceil(baseFactor * Constants.DEFAULT_LIGHT_ROUGH_TILE_OBSTACLES_LIMIT);
            case PIN, TEE -> 1;
            case WATER, SAND, OUT_OF_BOUNDS -> 0;
            default -> (int) Math.ceil(baseFactor * Constants.DEFAULT_TILE_OBSTACLES_LIMIT);
        };
    }

    private double getClusterDensityFactor(Tile tile) {
        double baseFactor = this.getCourseDifficulty().getDensityFactor();

        return switch (tile.getCollapsedState()) {
            case OBSTACLE -> baseFactor * 0.35;
            case FAIRWAY -> baseFactor * 0.2;
            case HEAVY_ROUGH -> baseFactor * 0.45;
            case LIGHT_ROUGH -> baseFactor * 0.4;
            case PIN -> baseFactor;
            default -> baseFactor * 0.3;
        };
    }

    private boolean shouldPlaceObstacle(Tile tile, ObstacleImpl obstacle, World world) {
        TileTypes tileType = tile.getCollapsedState();

        if (tileType == TileTypes.PIN || tileType == TileTypes.TEE) return true;

        if (tileType == TileTypes.WATER || tileType == TileTypes.OUT_OF_BOUNDS || tileType == TileTypes.SAND) {
            return false;
        }

        return this.isGroundValid(obstacle.getBoundingBox(), world);
    }

    private boolean isGroundValid(BoundingBox box, World world) {
        int minX = (int) Math.floor(box.getMinX());
        int maxX = (int) Math.ceil(box.getMaxX());
        int minZ = (int) Math.floor(box.getMinZ());
        int maxZ = (int) Math.ceil(box.getMaxZ());
        int y = (int) Math.floor(box.getMinY() - 1);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Block block = world.getBlockAt(x, y, z);
                if (block.isEmpty() || block.isLiquid() || block.getType() == Material.WATER) {
                    return false;
                }
            }
        }
        return true;
    }

    private ObstacleImpl selectObstacle(Tile tile) {

        final Map<TileTypes, List<WeightedObstacle>> obstacleWeights = Map.of(
                TileTypes.OBSTACLE, List.of(
                        new WeightedObstacle(registry.get("tree"), 0.8),
                        //new WeightedObstacle(registry.get("bigTree"), 0.2),
                        //new WeightedObstacle(registry.get("tallTree"), 0.25),
                        //new WeightedObstacle(registry.get("tallTree1"), 0.25),
                        new WeightedObstacle(registry.get("bush"), 0.2)
                ),
                TileTypes.FAIRWAY, List.of(
                        //new WeightedObstacle(registry.get("bigTree"), 0.2),
                        //new WeightedObstacle(registry.get("tallTree"), 0.15),
                        new WeightedObstacle(registry.get("rock"), 0.75),
                        new WeightedObstacle(registry.get("tree"), 0.25)
                ),
                TileTypes.PIN, List.of(
                        new WeightedObstacle(registry.get("pin"), 1.0)
                ),
                TileTypes.TEE, List.of(
                        new WeightedObstacle(registry.get("tee"), 1.0)
                ),
                TileTypes.HEAVY_ROUGH, List.of(
                    new WeightedObstacle(registry.get("tree"), 0.5),
                    new WeightedObstacle(registry.get("bush"), 0.5)
                ),
                TileTypes.LIGHT_ROUGH, List.of(
                    new WeightedObstacle(registry.get("bush"), 0.5),
                    new WeightedObstacle(registry.get("rock"), 0.5)
                )
        );

        List<WeightedObstacle> weightedList = obstacleWeights.get(tile.getCollapsedState());
        if (weightedList == null || weightedList.isEmpty())
            throw new RuntimeException("No obstacles defined for tile type: " + tile.getCollapsedState());

        double totalWeight = weightedList.stream().mapToDouble(WeightedObstacle::weight).sum();
        double r = random.nextDouble() * totalWeight;

        for (WeightedObstacle w : weightedList) {
            r -= w.weight();
            if (r <= 0) {
                return w.obstacle().clone();
            }
        }

        return weightedList.getLast().obstacle().clone();
    }
}
