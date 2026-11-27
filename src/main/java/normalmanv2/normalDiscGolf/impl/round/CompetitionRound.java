package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.player.DiscBag;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;

import java.util.UUID;

public abstract class CompetitionRound extends FFARound {
    private static final DiscBag STARTERS = new DiscBag();

    protected CompetitionRound(CourseImpl course, String id, DefaultRoundSettings settings) {
        super(course, id, settings);
    }

    public final DiscBag loanerBag() { return STARTERS; }
    public abstract boolean wind();
    public abstract int strokeLimit();

    public final boolean eligible(ScoreCard card, UUID player) {
        return card != null && card.getHoledOutCount() == getCourse().holes() && card.participated(player);
    }
}
