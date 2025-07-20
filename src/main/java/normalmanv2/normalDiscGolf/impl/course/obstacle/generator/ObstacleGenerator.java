package normalmanv2.normalDiscGolf.impl.course.obstacle.generator;

import com.sk89q.worldedit.math.BlockVector3;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.difficulty.CourseDifficulty;
import normalmanv2.normalDiscGolf.impl.course.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.obstacle.WeightedObstacle;
import normalmanv2.normalDiscGolf.impl.course.tile.Tile;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.normal.impl.registry.RegistryImpl;

import java.util.*;

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

    private void placeObstacle(Tile tile, World world) {
        int maxObstaclesPerTile = this.calculateMaxObstacles(tile);
        int clusters = Math.max(1, (int) Math.ceil(maxObstaclesPerTile * this.getClusterDensityFactor(tile)));

        // Random cluster centers within a tile.
        List<Location> clusterCenters = new ArrayList<>();
        double tileSize = Constants.DEFAULT_TILE_SIZE;
        Location tileBase = tile.getLocation();

        for (int i = 0; i < clusters; i++) {
            double cx = tileBase.getX() + random.nextDouble() * tileSize;
            double cz = tileBase.getZ() + random.nextDouble() * tileSize;
            clusterCenters.add(new Location(world, cx, tileBase.getY(), cz));
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
            double minX = tileBase.getX() + halfWidth;
            double maxX = tileBase.getX() + tileSize - halfWidth;
            double minZ = tileBase.getZ() + halfDepth;
            double maxZ = tileBase.getZ() + tileSize - halfDepth;

            double finalX = this.clamp(center.getX() + offsetX, minX, maxX);
            double finalZ = this.clamp(center.getZ() + offsetZ, minZ, maxZ);

            Location finalLoc = new Location(world, finalX, tileBase.getY(), finalZ);
            ObstacleImpl obstacle = prototype.clone();
            obstacle.setLocation(finalLoc);

            // Extra bound validation
            BoundingBox box = obstacle.getBoundingBox();
            boolean overlapsObstacle = this.placedObstacleBounds.stream().anyMatch(box::overlaps);
            boolean overlapsTile = this.placedTileBounds.stream().anyMatch(box::overlaps);

            // We can skip these tiles as there are no current plans for obstacle placements within these types.
            if (tile.getCollapsedState() == TileTypes.TEE
                    || tile.getCollapsedState() == TileTypes.WATER
                    || tile.getCollapsedState() == TileTypes.OUT_OF_BOUNDS) {

                if (overlapsTile) continue;
            }

            if (overlapsObstacle) continue;

            // Validates the area of which the obstacle is attempting to generate.
            if (shouldPlaceObstacle(tile, obstacle, world)) {
                obstacle.generate();
                this.placedObstacleBounds.add(box);
                placed++;
            }
        }
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private int calculateMaxObstacles(Tile tile) {
        double baseFactor = this.divisionCourseDifficulty.get(this.division).getDensityFactor();

        return switch (tile.getCollapsedState()) {
            case OBSTACLE -> (int) Math.ceil(baseFactor * Constants.DEFAULT_OBSTACLE_TILE_OBSTACLES_LIMIT);
            case FAIRWAY -> (int) Math.ceil(baseFactor * Constants.DEFAULT_FAIRWAY_TILE_OBSTACLES_LIMIT);
            case PIN -> 1;
            default -> (int) Math.ceil(baseFactor * Constants.DEFAULT_TILE_OBSTACLES_LIMIT);
        };
    }

    private double getClusterDensityFactor(Tile tile) {
        double baseFactor = this.divisionCourseDifficulty.get(this.division).getDensityFactor();

        return switch (tile.getCollapsedState()) {
            case OBSTACLE -> baseFactor * 0.25;
            case FAIRWAY -> baseFactor * 0.35;
            case PIN -> baseFactor * 0.6;
            default -> baseFactor * 0.3;
        };
    }

    private boolean shouldPlaceObstacle(Tile tile, ObstacleImpl obstacle, World world) {

        if (tile.getCollapsedState() == TileTypes.PIN) return true;
        if (this.isGroundValid(obstacle.getBoundingBox(), world)) return true;

        return tile.getCollapsedState() != TileTypes.WATER && tile.getCollapsedState() != TileTypes.TEE && tile.getCollapsedState() != TileTypes.OUT_OF_BOUNDS;
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
                        new WeightedObstacle(registry.get("tree"), 0.4),
                        new WeightedObstacle(registry.get("bigTree"), 0.2),
                        new WeightedObstacle(registry.get("tallTree"), 0.25),
                        new WeightedObstacle(registry.get("tallTree1"), 0.25),
                        new WeightedObstacle(registry.get("bush"), 0.2)
                ),
                TileTypes.FAIRWAY, List.of(
                        new WeightedObstacle(registry.get("bigTree"), 0.2),
                        new WeightedObstacle(registry.get("tallTree"), 0.15),
                        new WeightedObstacle(registry.get("rock"), 0.2),
                        new WeightedObstacle(registry.get("tree"), 0.25)
                ),
                TileTypes.PIN, List.of(
                        new WeightedObstacle(registry.get("pin"), 1.0)
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
