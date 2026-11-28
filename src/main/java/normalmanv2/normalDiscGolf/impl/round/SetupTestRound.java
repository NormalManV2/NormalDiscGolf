package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;

/** Private one-hole round used to check a server's setup without awarding progression. */
public final class SetupTestRound extends FFARound {
    public SetupTestRound(CourseImpl course, String id, DefaultRoundSettings settings) {
        super(course, id, settings);
    }
}
