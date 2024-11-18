package normalmanv2.normalDiscGolf.impl.course.obstacle;

import normalmanv2.normalDiscGolf.api.course.obstacle.Obstacle;
import org.bukkit.Location;

public class ObstacleImpl implements Obstacle {

    private Location location;
    private String schematicName;

    public ObstacleImpl(Location location, String schematicName) {
        this.location = location;
        this.schematicName = schematicName;
    }

    @Override
    public Location getLocation() {
        return this.location;
    }

    @Override
    public String getSchematicName() {
        return this.schematicName;
    }

    @Override
    public void setLocation(Location location) {
        this.location = location;
    }

    @Override
    public void setSchematicName(String schematicName) {
        this.schematicName = schematicName;
    }

    @Override
    public void getSchematicFile() {

    }

    @Override
    public boolean canGenerate() {
        return false;
    }

    @Override
    public boolean generate(Location location) {
        return false;
    }
}
