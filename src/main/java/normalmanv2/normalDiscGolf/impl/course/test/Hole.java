package normalmanv2.normalDiscGolf.impl.course.test;

import java.util.List;

public record Hole(int holeIndex, Coordinate tee, Coordinate pin, List<Coordinate> fairway, int par) {

}
