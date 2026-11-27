package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;

public final class BestShotRound extends DoublesRound {
    public BestShotRound(CourseImpl course, String id, DefaultRoundSettings settings) {
        super(course, id, settings);
    }
}
