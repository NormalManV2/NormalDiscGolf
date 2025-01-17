package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.api.course.Course;
import normalmanv2.normalDiscGolf.api.division.Division;
import normalmanv2.normalDiscGolf.impl.course.obstacle.ObstacleImpl;
import normalmanv2.normalDiscGolf.impl.registry.ObstacleRegistry;
import org.bukkit.Location;
import org.normal.impl.RegistryImpl;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class CourseImpl implements Course {

    private final Division division;
    private final CourseGrid grid;
    private final String courseName;
    private Map<Integer, Location> teeLocations;
    private Map<Integer, Location> holeLocations;
    private Map<Integer, Integer> holePars;

    public CourseImpl(Division division, CourseGrid grid, String courseName) {
        this.division = division;
        this.grid = grid;
        this.courseName = courseName;
    }

    public void generateCourseGrid(RegistryImpl<String, ObstacleImpl> registry) {
        this.grid.generate(registry, this.division);
        this.teeLocations = this.grid.getTeeLocations();
        this.holeLocations = this.grid.getHoleLocations();
        this.holePars = this.grid.getHolePars();
    }

    @Override
    public String name() {
        if (this.courseName == null) throw new IllegalStateException("Course name not set!");
        return this.courseName;
    }

    @Override
    public int holes() {
        return 0;
    }

    @Override
    public Location startingLocation() {
        return null;
    }

    @Override
    public Map<Integer, Location> teeLocations() {
        return Collections.unmodifiableMap(this.teeLocations);
    }

    @Override
    public Map<Integer, Location> holeLocations() {
        return Collections.unmodifiableMap(this.holeLocations);
    }

    @Override
    public Map<Integer, Integer> holePars() {
        return Collections.unmodifiableMap(this.holePars);
    }

}
