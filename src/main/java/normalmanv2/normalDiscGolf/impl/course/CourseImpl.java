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

    public CourseImpl(Division division, CourseGrid grid) {
        this.division = division;
        this.grid = grid;
    }

    public void generateCourseGrid(RegistryImpl<String, ObstacleImpl> registry) {
        this.grid.generate(registry, this.division);
    }

    @Override
    public String name() {
        return "";
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
    public Set<Location> holeLocations() {
        return Collections.unmodifiableSet(this.holeLocations);
    }

    @Override
    public Map<Integer, Integer> holePars() {
        return Collections.unmodifiableMap(this.holePars);
    }

}
