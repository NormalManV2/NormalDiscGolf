package normalmanv2.normalDiscGolf.impl.course.objects;

import org.bukkit.Location;

public interface IObstacle {

    Location getLocation();
    String getSchematicName();
    void setLocation(Location location);
    void setSchematicName(String schematicName);
    void getSchematicFile();
}
