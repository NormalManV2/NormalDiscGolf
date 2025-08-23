package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;

public class FFARound extends RoundImpl {

    public FFARound(
            CourseImpl course,
            String id,
            DefaultRoundSettings settings,
            DefaultRoundTurnManager turnManager,
            DefaultRoundTeamManager teamManager,
            DefaultRoundLifecycle lifecycle,
            DefaultRoundStrokeManager strokeManager,
            DefaultRoundScoreCardManager scoreCardManager) {
        super(course, id, settings, turnManager, teamManager, lifecycle, strokeManager, scoreCardManager);
    }
}