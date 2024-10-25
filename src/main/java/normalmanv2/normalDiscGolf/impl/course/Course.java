package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.impl.course.objects.Obstacle;
import normalmanv2.normalDiscGolf.impl.round.CourseDifficulty;
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
    private static final Map<Obstacle, Location> obstacleLocations = new HashMap<>();

    public Course(CourseDifficulty difficulty, String name, int holes, Location startingLocation) {
        this.difficulty = difficulty;
        this.name = name;
        this.holes = holes;
        this.startingLocation = startingLocation;
        this.holeLocations = new HashSet<>();
    }

    public static void registerObstacle(Obstacle obstacle) {
        obstacleLocations.put(obstacle, obstacle.getLocation());
    }

    public static void unregisterObstacle(Obstacle obstacle) {
        obstacleLocations.remove(obstacle);
    }

    public void clearAndSetHoleLocations(Set<Location> locations) {
        this.holeLocations.clear();
        this.holeLocations.addAll(locations);
    }

    public void setHoleLocation(Location location) {
        this.holeLocations.add(location);
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

    public Map<Obstacle, Location> getObstacleLocations() {
        return Collections.unmodifiableMap(obstacleLocations);
    }



}
