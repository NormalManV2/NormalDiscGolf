package normalmanv2.normalDiscGolf.api.round.manager;

import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;

import java.util.Map;
import java.util.UUID;

public interface RoundScoreCardManager {

    Map<Team, ScoreCard> getScoreCards();
    ScoreCard getScoreCardByTeam(Team team);
    ScoreCard getScoreCardById(UUID playerId);
    void addScoreCard(Team team, ScoreCard scoreCard);
    void removeScoreCard(Team team);
    void clearScores();

}
