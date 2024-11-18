package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.impl.event.GoalScoreEvent;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class GoalScoreListener implements Listener {

    @EventHandler
    public void onGoalScore(GoalScoreEvent event) {
        handleScoring((FFARound) event.getRound(), event.getPlayer().getUniqueId());
        System.out.println("GoalScoreEventListener Invoked");
    }

    private void handleScoring(FFARound round, UUID playerId) {

        for (TeamImpl team : round.getScoreCards().keySet()) {
            if (team.getTeamMembers().contains(playerId)) {
                ScoreCard scoreCard = round.getScoreCards().get(team);
                scoreCard.recordHoleScore(1, scoreCard.getCurrentStrokes(), 3);
                scoreCard.resetCurrentStrokes();
            }
        }
    }
}
