package normalmanv2.normalDiscGolf.impl.course.obstacle.generator;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.difficulty.CourseDifficulty;
import normalmanv2.normalDiscGolf.impl.course.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.tile.Tile;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;
import normalmanv2.normalDiscGolf.impl.course.obstacle.obstacles.Pin;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Location;
import org.bukkit.World;
import org.normal.impl.registry.RegistryImpl;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class ObstacleGenerator {

    private final CourseGrid courseGrid;
    private final Random random;
    private final RegistryImpl<String, ObstacleImpl> registry;
    private final Division division;
    private final Map<Division, CourseDifficulty> divisionCourseDifficulty;

    public ObstacleGenerator(CourseGrid courseGrid, RegistryImpl<String, ObstacleImpl> registry, Division division) {
        this.courseGrid = courseGrid;
        this.random = new Random();
        this.registry = registry;
        this.division = division;
        this.divisionCourseDifficulty = NDGManager.getInstance().getDivisionCourseRegistry().getBackingMap();
    }

    public void generateObstacles(World world) {
        for (int x = 0; x < courseGrid.getWidth(); x++) {
            for (int z = 0; z < courseGrid.getDepth(); z++) {
                placeObstacle(courseGrid.getTile(x, z), world);
            }
        }
    }

    private void placeObstacle(Tile tile, World world) {
        int maxObstaclesPerTile = calculateMaxObstacles(tile);

        int gridSize = Constants.DEFAULT_TILE_SIZE / 4;
        double subRegionSize = Constants.DEFAULT_TILE_SIZE / 8.0;

        Set<String> occupiedSubRegions = new HashSet<>();

        for (int i = 0; i < maxObstaclesPerTile; i++) {
            int gridX = random.nextInt(gridSize);
            int gridZ = random.nextInt(gridSize);

            String regionKey = gridX + "," + gridZ;
            if (occupiedSubRegions.contains(regionKey)) {
                continue;
            }
            occupiedSubRegions.add(regionKey);

            double offsetX = random.nextDouble() * subRegionSize + gridX * subRegionSize;
            double offsetZ = random.nextDouble() * subRegionSize + gridZ * subRegionSize;

            Location obstacleLocation = new Location(world,
                    tile.getLocation().getX() + offsetX,
                    tile.getLocation().getY(),
                    tile.getLocation().getZ() + offsetZ
            );

            ObstacleImpl obstacle = selectObstacle(tile);

            if (obstacle == null) {
                throw new RuntimeException("Obstacle is null! This is a major issue, please report!");
            }

            obstacle.setLocation(obstacleLocation);

            if (shouldPlaceObstacle(tile, obstacle)) {
                obstacle.generate();
            }
        }
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

    private boolean shouldPlaceObstacle(Tile tile, ObstacleImpl obstacle) {

        if (obstacle instanceof Pin && tile.getCollapsedState() == TileTypes.PIN) return true;

        if (tile.getCollapsedState() == TileTypes.WATER || tile.getCollapsedState() == TileTypes.TEE) {
            return false;
        }

        double chance = random.nextDouble();

        return chance <= this.divisionCourseDifficulty.get(this.division).getDensityFactor();
    }

    private ObstacleImpl selectObstacle(Tile tile) {
        return switch (tile.getCollapsedState()) {
            case OBSTACLE -> random.nextDouble() < 0.5
                    ? this.registry.get("tree")
                    : this.registry.get("bush");
            case FAIRWAY -> random.nextDouble() < 0.5
                    ? this.registry.get("rock")
                    : this.registry.get("tree");
            case PIN -> this.registry.get("pin");
            default -> null;
        };

    }
}
