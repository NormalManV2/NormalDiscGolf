package normalmanv2.normalDiscGolf.api.round.delegate.manager;

import normalmanv2.normalDiscGolf.api.round.manager.RoundScoreCardManager;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;

import java.util.Map;
import java.util.UUID;

public interface DelegateRoundScoreCardManager extends RoundScoreCardManager {
    RoundScoreCardManager getRoundScoreCardManager();

    @Override
    default ScoreCard getScoreCardByTeam(Team team) {
        return this.getRoundScoreCardManager().getScoreCardByTeam(team);
    }

    @Override
    default ScoreCard getScoreCardById(UUID playerId) {
        return this.getRoundScoreCardManager().getScoreCardById(playerId);
    }

    @Override
    default Map<Team, ScoreCard> getScoreCards() {
        return this.getRoundScoreCardManager().getScoreCards();
    }

    @Override
    default void addScoreCard(Team team, ScoreCard scoreCard) {
        this.getRoundScoreCardManager().addScoreCard(team, scoreCard);
    }

    @Override
    default void removeScoreCard(Team team) {
        this.getRoundScoreCardManager().removeScoreCard(team);
    }

    @Override
    default void clearScores() {
        this.getRoundScoreCardManager().clearScores();
    }
}
