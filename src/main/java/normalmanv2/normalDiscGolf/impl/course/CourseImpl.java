package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.api.course.Course;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.difficulty.CourseDifficulty;
import org.bukkit.Location;

import java.util.Collections;
import java.util.Map;


public class CourseImpl implements Course {

    private final Division division;
    private final String courseName;
    private final CourseGrid grid;
    private final Map<Integer, Location> teeLocations;
    private final Map<Integer, Location> holeLocations;
    private final Map<Integer, Integer> holePars;

    public CourseImpl(Division division, CourseGrid grid, String courseName) {
        this.division = division;
        this.courseName = courseName;
        grid.generate(division);
        this.grid = grid;
        this.teeLocations = grid.getTeeLocations();
        this.holeLocations = grid.getHoleLocations();
        this.holePars = grid.getHolePars();
    }

    @Override
    public String name() {
        if (this.courseName == null) throw new IllegalStateException("Course name not set!");
        return this.courseName;
    }

    @Override
    public int holes() {
        return this.grid.getNumHoles();
    }

    @Override
    public Location startingLocation() {
        return this.teeLocations.get(0);
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

    public Division division() {
        return this.division;
    }

    public CourseDifficulty difficulty() {
        return this.division.getDifficulty();
    }

    public CourseGrid grid() {
        return this.grid;
    }

}
