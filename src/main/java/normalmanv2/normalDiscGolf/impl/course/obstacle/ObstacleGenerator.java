package normalmanv2.normalDiscGolf.impl.course.obstacle;

import normalmanv2.normalDiscGolf.impl.course.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.Tile;

import java.util.Random;

public class ObstacleGenerator {

    private final CourseGrid courseGrid;
    private final Random random;

    public ObstacleGenerator(CourseGrid courseGrid) {
        this.courseGrid = courseGrid;
        this.random = new Random();
    }

    public void generateObstacles() {
        for (int x = 0; x < courseGrid.getWidth(); x++) {
            for (int z = 0; z < courseGrid.getDepth(); z++) {
                placeObstacle(courseGrid.getTile(x,z));
            }
        }
    }

    private void placeObstacle(Tile tile) {
        ObstacleImpl obstacle;

        switch (tile.getCollapsedState()) {
            case OBSTACLE -> {
                if (random.nextBoolean()) {
                    obstacle = new Tree(tile.getLocation(), "tree.schem");
                } else {
                    obstacle = new Bush(tile.getLocation(), "bush.schem");
                }
                if (shouldPlaceObstacle(tile, obstacle)) {
                    tile.setObstacle(obstacle, obstacle.getLocation());
                }
            }
            case FAIRWAY -> {
                obstacle = new Rock(tile.getLocation(), "rock.schem");
                if (shouldPlaceObstacle(tile, obstacle)) {
                    tile.setObstacle(obstacle, obstacle.getLocation());
                }
            }
        }
    }

    private boolean shouldPlaceObstacle(Tile tile, ObstacleImpl obstacle) {
        return random.nextBoolean();
    }

}
