package normalmanv2.normalDiscGolf.impl.course.creator;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;

import java.util.List;

public class CourseCreator {

    private Division division;
    private String name;
    private int holes;
    private int size;

    public CourseImpl create(Division division, CourseGrid grid, String courseName) {
        if (division == null) throw new IllegalArgumentException("Course division must be set");
        if (grid == null) throw new IllegalArgumentException("Course grid must be set");
        if (courseName == null || courseName.isBlank()) throw new IllegalArgumentException("Course name must be set");
        return new CourseImpl(division, grid, courseName);
    }

    public CourseImpl create(int size, int numHoles, Division division, String name) {
        if (size <= 0) throw new IllegalArgumentException("Course size must be greater than zero");
        if (numHoles <= 0) throw new IllegalArgumentException("Course holes must be greater than zero");
        if (division == null) throw new IllegalArgumentException("Course division must be set");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Course name must be set");

        CourseGrid grid = new CourseGrid(size, size, List.of(TileTypes.values()), numHoles);
        return new CourseImpl(division, grid, name);
    }

    public CourseImpl create() {

        if (this.size <= 0) throw new IllegalStateException("Course size must be set");
        if (this.holes <= 0) throw new IllegalStateException("Course holes must be set");
        if (this.division == null || this.name == null) throw new IllegalStateException("Course division and name must be set");

        CourseGrid grid = new CourseGrid(this.size, this.size, List.of(TileTypes.values()), this.holes);
        return new CourseImpl(this.division, grid, this.name);
    }

    public CourseCreator setDivision(Division division) {
        this.division = division;
        return this;
    }

    public CourseCreator setName(String name) {
        this.name = name;
        return this;
    }

    public CourseCreator setHoles(int holes) {
        this.holes = holes;
        return this;
    }

    public CourseCreator setSize(int size) {
        this.size = size;
        return this;
    }
}
