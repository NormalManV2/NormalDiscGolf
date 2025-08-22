package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.round.manager.RoundScoreCardManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundScoreCardManager;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DefaultRoundScoreCardManager implements DelegateRoundScoreCardManager {

    private final Map<Team, ScoreCard> scoreCards;

    public DefaultRoundScoreCardManager() {
        this.scoreCards = new HashMap<>();
    }

    @Override
    public RoundScoreCardManager getRoundScoreCardManager() {
        return this;
    }

    @Override
    public ScoreCard getScoreCardByTeam(Team team) {
        return this.scoreCards.get(team);
    }

    @Override
    public ScoreCard getScoreCardById(UUID playerId) {
        return this.scoreCards.keySet()
                .stream()
                .filter(team -> team.contains(playerId))
                .findFirst()
                .map(this.scoreCards::get)
                .orElse(null);

    }

    @Override
    public Map<Team, ScoreCard> getScoreCards() {
        return Collections.unmodifiableMap(this.scoreCards);
    }

    @Override
    public void addScoreCard(Team team, ScoreCard scoreCard) {
        this.scoreCards.put(team, scoreCard);
    }

    @Override
    public void removeScoreCard(Team team) {
        this.scoreCards.remove(team);
    }

    @Override
    public void clearScores() {
        this.scoreCards.clear();
    }
}
