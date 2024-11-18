package normalmanv2.normalDiscGolf.api.course;

import normalmanv2.normalDiscGolf.api.course.obstacle.Obstacle;
import org.bukkit.Location;

import java.util.Map;
import java.util.Set;

public interface Course {

    String getName();
    int getHoles();
    Location getStartingLocation();
    Map<Integer, Location> getTeeLocations();
    Set<Location> getHoleLocations();
    Map<Obstacle, Location> getObstacleLocations();
    Map<Integer, Integer> getHolePars();

}
