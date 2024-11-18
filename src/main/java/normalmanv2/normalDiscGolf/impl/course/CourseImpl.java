package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.api.course.Course;
import normalmanv2.normalDiscGolf.api.course.obstacle.Obstacle;
import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;
import org.bukkit.Location;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class CourseImpl implements Course {

    private final CourseDifficulty difficulty;
    private final String name;
    private final int holes;
    private final Location startingLocation;
    private final Map<Integer, Location> teeLocations;
    private final Set<Location> holeLocations;
    private final Map<ObstacleImpl, Location> obstacleLocations;
    private final Map<Integer, Integer> holePars;
    private final CourseGrid grid;

    public CourseImpl(CourseDifficulty difficulty, String name, int holes, Location startingLocation, CourseGrid grid) {
        this.difficulty = difficulty;
        this.name = name;
        this.holes = holes;
        this.startingLocation = startingLocation;
        this.teeLocations = new HashMap<>();
        this.holeLocations = new HashSet<>();
        this.obstacleLocations = new HashMap<>();
        this.holePars = new HashMap<>();
        this.grid = grid;
    }

    public void registerObstacle(ObstacleImpl obstacleImpl) {
        this.obstacleLocations.put(obstacleImpl, obstacleImpl.getLocation());
    }

    public void unregisterObstacle(ObstacleImpl obstacleImpl) {
        this.obstacleLocations.remove(obstacleImpl);
    }

    public void clearAndSetHoleLocations(Set<Location> locations) {
        this.holeLocations.clear();
        this.holeLocations.addAll(locations);
    }

    public void setHoleLocation(Location location) {
        this.holeLocations.add(location);
    }

    public void generateCourseGrid() {
        this.grid.generate();
    }

    public CourseDifficulty getDifficulty() {
        return this.difficulty;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public int getHoles() {
        return this.holes;
    }

    @Override
    public Location getStartingLocation() {
        return this.startingLocation;
    }

    @Override
    public Map<Integer, Location> getTeeLocations() {
        return Collections.unmodifiableMap(this.teeLocations);
    }

    @Override
    public Set<Location> getHoleLocations() {
        return Collections.unmodifiableSet(this.holeLocations);
    }

    @Override
    public Map<Obstacle, Location> getObstacleLocations() {
        return Collections.unmodifiableMap(obstacleLocations);
    }

    @Override
    public Map<Integer, Integer> getHolePars() {
        return Collections.unmodifiableMap(this.holePars);
    }

    public CourseGrid getGrid() {
        return this.grid;
    }

}
