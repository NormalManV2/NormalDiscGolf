package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.impl.course.objects.ObstacleImpl;
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
    private final Map<Integer, Location> teeLocations;
    private final Set<Location> holeLocations;
    private static final Map<ObstacleImpl, Location> obstacleLocations = new HashMap<>();
    private final Map<Integer, Integer> holePars;

    public Course(CourseDifficulty difficulty, String name, int holes, Location startingLocation) {
        this.difficulty = difficulty;
        this.name = name;
        this.holes = holes;
        this.startingLocation = startingLocation;
        this.teeLocations = new HashMap<>();
        this.holeLocations = new HashSet<>();
        this.holePars = new HashMap<>();
    }

    public static void registerObstacle(ObstacleImpl obstacleImpl) {
        obstacleLocations.put(obstacleImpl, obstacleImpl.getLocation());
    }

    public static void unregisterObstacle(ObstacleImpl obstacleImpl) {
        obstacleLocations.remove(obstacleImpl);
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

    public Map<ObstacleImpl, Location> getObstacleLocations() {
        return Collections.unmodifiableMap(obstacleLocations);
    }



}
