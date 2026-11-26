package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.api.course.Course;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;


public class CourseImpl implements Course {
    private final Division division;
    private final String courseName;
    private final CourseGrid grid;

    public CourseImpl(Division division, CourseGrid grid, String courseName) {
        this.division = division;
        this.courseName = courseName;
        this.grid = grid;
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
    public CourseGrid.GridPoint startingLocation() {
        return this.grid.getTeePoints().get(0);
    }

    @Override
    public Location toLocation(World world, CourseGrid.GridPoint gridPoint) {
        return new Location(world, gridPoint.x(), gridPoint.y(), gridPoint.z());
    }

    @Override
    public Map<Integer, CourseGrid.GridPoint> teeLocations() {
        return this.grid.getTeePoints();
    }

    @Override
    public Map<Integer, CourseGrid.GridPoint> holeLocations() {
        return this.grid.getHolePoints();
    }

    @Override
    public Map<Integer, Integer> holePars() {
        return this.grid.getHolePars();
    }

    public Division division() {
        return this.division;
    }

    public Location tee(World world, int hole) {
        var point = teeLocations().get(hole);
        Location location = new Location(world, point.x() + .5, grid.surfaceHeight(point.x(), point.z()) + 1, point.z() + .5);
        location.setDirection(basket(world, hole).toVector().subtract(location.toVector()));
        return location;
    }
    public Location basket(World world, int hole) {
        var point = holeLocations().get(hole);
        return new Location(world, point.x() + .5, grid.surfaceHeight(point.x(), point.z()) + 1.9, point.z() + .5);
    }
    public org.bukkit.util.BoundingBox basketBounds(World world, int hole) {
        Location center = basket(world, hole);
        return org.bukkit.util.BoundingBox.of(center, 1.3, 1.0, 1.3);
    }

    public CourseGrid grid() {
        return this.grid;
    }

}
