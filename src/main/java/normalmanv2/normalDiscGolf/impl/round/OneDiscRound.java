package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;

public final class OneDiscRound extends FFARound {
    public OneDiscRound(CourseImpl course, String id, DefaultRoundSettings settings) {
        super(course, id, settings);
    }
}
