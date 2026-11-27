package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.competition.DailyCourse;
public final class DailyRound extends CompetitionRound {
    private final DailyRoundGhosts ghosts = new DailyRoundGhosts(this);

    public DailyRound(DailyCourse course, String id, DefaultRoundSettings settings) {
        super(course, id, settings);
    }

    @Override public DailyCourse getCourse() { return (DailyCourse)super.getCourse(); }
    public DailyCourse.Definition definition() { return getCourse().definition(); }
    public DailyRoundGhosts ghosts() { return ghosts; }
    @Override public boolean wind() { return definition().wind(); }
    @Override public int strokeLimit() { return definition().strokeLimit(); }
}
