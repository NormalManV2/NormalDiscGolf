package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.common.round.DefaultRoundSettings;
import normalmanv2.normalDiscGolf.impl.competition.WeeklyCourse;
import normalmanv2.normalDiscGolf.impl.competition.WeeklyTournament;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class WeeklyTournamentRound extends CompetitionRound {
    private final Map<UUID, ScoreCard> completedDepartures = new HashMap<>();
    public WeeklyTournamentRound(WeeklyCourse course, String id, DefaultRoundSettings settings) {
        super(course, id, settings);
    }

    @Override public WeeklyCourse getCourse() { return (WeeklyCourse)super.getCourse(); }
    @Override public boolean wind() { return getCourse().wind(); }
    @Override public int strokeLimit() { return getCourse().strokeLimit(); }
    public WeeklyTournament.Definition event() { return getCourse().event(); }

    void retainCompletedCard(UUID player) {
        ScoreCard card = getScoreCardById(player);
        if (eligible(card, player)) completedDepartures.put(player, card);
    }

    boolean hasCompletedDepartures() { return !completedDepartures.isEmpty(); }

    public ScoreCard finalCard(UUID player) {
        ScoreCard departed = completedDepartures.get(player);
        return departed == null ? getScoreCardById(player) : departed;
    }
}
