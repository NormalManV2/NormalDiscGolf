package normalmanv2.normalDiscGolf.api.round;

import normalmanv2.normalDiscGolf.api.course.Course;

public interface GameRound extends RoundLifecycle, RoundTeamManager, RoundTurnManager, RoundSettings {

    String getId();

    Course getCourse();
}