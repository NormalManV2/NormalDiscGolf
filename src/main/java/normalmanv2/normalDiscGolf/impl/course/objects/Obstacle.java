package normalmanv2.normalDiscGolf.impl.course.objects;

import org.bukkit.Location;

public class Obstacle implements normalmanv2.normalDiscGolf.api.Obstacle {

    private Location location;
    private String schematicName;

    public Obstacle(Location location, String schematicName) {
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
}
