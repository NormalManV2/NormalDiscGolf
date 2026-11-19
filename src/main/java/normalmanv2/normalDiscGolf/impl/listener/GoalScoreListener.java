package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.event.GoalScoreEvent;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class GoalScoreListener implements Listener {

    @EventHandler
    public void onGoalScore(GoalScoreEvent event) {
        Team scoringTeam = this.findPlayerTeam(event.getRound(), event.getPlayer().getUniqueId());
        if (scoringTeam == null) {
            return;
        }

        if (this.handleScoring(event.getHoleNumber(), event.getRound(), scoringTeam)) {
            event.getRound().setTeamScored(event.getHoleNumber(), scoringTeam);
        }
    }

    private boolean handleScoring(int holeNumber, GameRound round, Team team) {
        ScoreCard scoreCard = round.getScoreCardByTeam(team);
        Integer par = round.getCourse().holePars().get(holeNumber);
        if (scoreCard == null || par == null || scoreCard.getHoleScores().containsKey(holeNumber)) {
            return false;
        }

        scoreCard.recordHoleScore(holeNumber, scoreCard.getCurrentStrokes(), par);
        scoreCard.resetCurrentStrokes();
        return true;
    }

    private Team findPlayerTeam(GameRound round, UUID playerId) {
        for (Team foundTeam : round.getTeams()) {
            if (foundTeam.getTeamMembers().contains(playerId)) {
                return foundTeam;
            }
        }

        return null;
    }

}
