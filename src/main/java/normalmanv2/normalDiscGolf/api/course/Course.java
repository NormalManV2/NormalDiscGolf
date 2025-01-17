package normalmanv2.normalDiscGolf.api.course;

import org.bukkit.Location;

import java.util.Map;
import java.util.Set;

public interface Course {

    String name();
    int holes();
    Location startingLocation();
    Map<Integer, Location> teeLocations();
    Map<Integer, Location> holeLocations();
    Map<Integer, Integer> holePars();

}
