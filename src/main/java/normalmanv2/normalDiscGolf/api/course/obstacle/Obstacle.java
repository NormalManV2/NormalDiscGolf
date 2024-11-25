package normalmanv2.normalDiscGolf.api.course.obstacle;

import org.bukkit.Location;

public interface Obstacle {

    Location getLocation();
    String getSchematicName();
    void setLocation(Location location);
    boolean generate(Location location);
}
