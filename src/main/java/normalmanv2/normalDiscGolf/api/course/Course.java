package normalmanv2.normalDiscGolf.api.course;

import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;

public interface Course {
    String name();
    int holes();
    CourseGrid.GridPoint startingLocation();
    Map<Integer, CourseGrid.GridPoint> teeLocations();
    Map<Integer, CourseGrid.GridPoint> holeLocations();
    Map<Integer, Integer> holePars();

    Location toLocation(World world, CourseGrid.GridPoint gridPoint);
}
