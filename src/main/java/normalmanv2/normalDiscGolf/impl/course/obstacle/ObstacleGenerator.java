package normalmanv2.normalDiscGolf.impl.course.obstacle;

import normalmanv2.normalDiscGolf.api.division.Division;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseDifficulty;
import normalmanv2.normalDiscGolf.impl.course.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.Tile;
import normalmanv2.normalDiscGolf.impl.course.TileTypes;
import normalmanv2.normalDiscGolf.impl.registry.ObstacleRegistry;
import org.bukkit.Location;
import org.normal.impl.RegistryImpl;

import java.util.HashMap;
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

    public void generateObstacles() {
        for (int x = 0; x < courseGrid.getWidth(); x++) {
            for (int z = 0; z < courseGrid.getDepth(); z++) {
                placeObstacle(courseGrid.getTile(x, z));
            }
        }
    }

    private void placeObstacle(Tile tile) {
        int maxObstaclesPerTile = calculateMaxObstacles(tile);

        int gridSize = 4;
        double subRegionSize = 8.0 / gridSize;

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

            Location obstacleLocation = new Location(tile.getLocation().getWorld(),
                    tile.getLocation().getX() + offsetX,
                    tile.getLocation().getY(),
                    tile.getLocation().getZ() + offsetZ
            );

            ObstacleImpl obstacle = selectObstacle(tile);

            if (obstacle == null) {
                System.out.println("Obstacle is null! (ObstacleGenerator)");
                return;
            }

                obstacle.setLocation(obstacleLocation);

                if (obstacle instanceof Pin) {
                    obstacle.generate(obstacleLocation);
                }

                if (shouldPlaceObstacle(tile, obstacle)) {

                    if (obstacle.generate(obstacleLocation)) {
                        System.out.println("Obstacle " + obstacle + "generated at " + obstacleLocation);
                    } else {
                        System.out.println("Obstacle unable to generate " + obstacle + "at " + obstacleLocation);
                    }
                } else {
                    System.out.println("Obstacle skipped due to density check " + obstacle);
                }

        }
    }

    private int calculateMaxObstacles(Tile tile) {
        double baseFactor = this.divisionCourseDifficulty.get(this.division).getDensityFactor();

        return switch (tile.getCollapsedState()) {
            case OBSTACLE -> (int) Math.ceil(baseFactor * 8);
            case FAIRWAY -> (int) Math.ceil(baseFactor * 5);
            case PIN -> 1;
            default -> (int) Math.ceil(baseFactor * 4);
        };
    }

    private boolean shouldPlaceObstacle(Tile tile, ObstacleImpl obstacle) {

        if (obstacle instanceof Pin) return true;

        if (tile.getCollapsedState() == TileTypes.WATER || tile.getCollapsedState() == TileTypes.TEE) {
            System.out.println("Obstacle cannot be placed in water or tee tiles!");
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
                    : this.registry.get("bush");
            case PIN -> this.registry.get("pin");
            default -> null;
        };

    }
}
