package normalmanv2.normalDiscGolf.course;

import normalmanv2.normalDiscGolf.course.objects.Object;
import normalmanv2.normalDiscGolf.round.CourseDifficulty;
import org.bukkit.Location;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Course {

    private final CourseDifficulty difficulty;
    private final String name;
    private final int holes;
    private final Location startingLocation;
    private final Set<Location> holeLocations;
    private final Map<Object, Location> objectLocations;

    public Course(CourseDifficulty difficulty, String name, int holes, Location startingLocation) {
        this.difficulty = difficulty;
        this.name = name;
        this.holes = holes;
        this.startingLocation = startingLocation;
        this.holeLocations = new HashSet<>();
        this.objectLocations = new HashMap<>();
    }

    public CourseDifficulty getDifficulty() {
        return this.difficulty;
    }

    public String getName() {
        return this.name;
    }

    public int getHoles() {
        return this.holes;
    }

    public Location getStartingLocation() {
        return this.startingLocation;
    }

    public Set<Location> getHoleLocations() {
        return Collections.unmodifiableSet(this.holeLocations);
    }

    public Map<Object, Location> getObjectLocations() {
        return Collections.unmodifiableMap(this.objectLocations);
    }

}
