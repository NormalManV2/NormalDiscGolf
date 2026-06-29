package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.event.GoalScoreEvent;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class GoalScoreListener implements Listener {

    @EventHandler
    public void onGoalScore(GoalScoreEvent event) {
        this.handleScoring(event.getHoleNumber(), event.getRound(), event.getPlayer().getUniqueId());
        this.updateScoredMap(event.getPlayer().getUniqueId(), (RoundImpl) event.getRound(), event.getHoleNumber());
    }

    private void handleScoring(int holeNumber, GameRound round, UUID playerId) {

        if (round instanceof FFARound ffaRound) {
            for (Team team : ffaRound.getTeams()) {
                if (!team.getTeamMembers().contains(playerId)) {
                    continue;
                }

                ScoreCard scoreCard = ffaRound.getScoreCardByTeam(team);
                Integer par = ffaRound.getCourse().holePars().get(holeNumber);
                if (scoreCard == null || par == null || scoreCard.getHoleScores().containsKey(holeNumber)) {
                    continue;
                }

                scoreCard.recordHoleScore(holeNumber, scoreCard.getCurrentStrokes(), par);
                scoreCard.resetCurrentStrokes();
            }
        }
    }

    private void updateScoredMap(UUID playerId, RoundImpl round, int holeNumber) {
        for (Team foundTeam : round.getTeams()) {
            if (foundTeam.getTeamMembers().contains(playerId)) {
                round.setTeamScored(holeNumber, foundTeam);
            }
        }
    }

}
