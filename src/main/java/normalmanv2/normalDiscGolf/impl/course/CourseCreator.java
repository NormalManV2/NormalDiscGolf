package normalmanv2.normalDiscGolf.impl.course;

import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.impl.course.tile.TileTypes;
import normalmanv2.normalDiscGolf.impl.util.Constants;

import java.util.List;

public class CourseCreator {

    private Division division;
    private String name;
    private int holes;
    private int size;

    public CourseImpl create(Division division, CourseGrid grid, String courseName) {
        return new CourseImpl(division, grid, courseName);
    }

    public CourseImpl create(int size, int numHoles, Division division, String name) {
        CourseGrid grid = new CourseGrid(size, size, List.of(TileTypes.values()), numHoles);
        return new CourseImpl(division, grid, name);
    }

    public CourseImpl create() {

        if (this.size == 0) throw new IllegalStateException("Course size must be set");
        if (this.holes == 0) throw new IllegalStateException("Course holes must be set");
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
