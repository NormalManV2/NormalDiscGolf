package normalmanv2.normalDiscGolf.impl.course;

import org.bukkit.Location;

public interface Obstacle {

    Location getLocation();
    String getSchematicName();
    void setLocation(Location location);
    void setSchematicName(String schematicName);
    void getSchematicFile();
}
