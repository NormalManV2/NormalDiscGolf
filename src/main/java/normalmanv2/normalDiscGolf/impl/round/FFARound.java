package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;

public class FFARound extends RoundImpl {

    public FFARound(
            CourseImpl course,
            String id,
            DefaultRoundSettings settings) {
        super(course, id, settings);
    }
}