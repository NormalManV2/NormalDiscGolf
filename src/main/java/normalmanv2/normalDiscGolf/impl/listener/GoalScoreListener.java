package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.event.GoalScoreEvent;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.round.EliminationRound;
import normalmanv2.normalDiscGolf.impl.round.AceRaceRound;
import normalmanv2.normalDiscGolf.impl.round.ClosestToPinRound;
import normalmanv2.normalDiscGolf.impl.round.SingleThrowRound;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

public class GoalScoreListener implements Listener {

    @EventHandler
    public void onGoalScore(GoalScoreEvent event) {
        if (event.getRound().isOver() || event.getHoleNumber() != event.getRound().getCurrentHole()) return;
        Team scoringTeam = this.findPlayerTeam(event.getRound(), event.getPlayer().getUniqueId());
        if (scoringTeam == null) {
            return;
        }

        if (this.handleScoring(event, scoringTeam)) {
            if (event.getRound() instanceof EliminationRound elimination)
                elimination.recordBasket(event.getPlayer().getUniqueId());
            event.getRound().setTeamScored(event.getHoleNumber(), scoringTeam);
            var play = ((normalmanv2.normalDiscGolf.impl.round.RoundImpl) event.getRound()).play();
            ScoreCard card = event.getRound().getScoreCardByTeam(scoringTeam);
            ScoreCard.Basket basket = card.getBaskets().get(event.getHoleNumber());
            String callout = basketCallout(basket);
            if (event.getRound() instanceof AceRaceRound) {
                play.message("§6" + event.getPlayer().getName() + " aced hole " + (event.getHoleNumber() + 1)
                        + "! (" + AceRaceRound.aceCount(card.getHoledOutCount()) + ")");
            } else if (event.getRound() instanceof ClosestToPinRound closest) {
                closest.recordDistance(scoringTeam, 0);
            } else {
                play.message("§a" + event.getPlayer().getName() + " completed hole " + (event.getHoleNumber() + 1) + "!"
                        + (basket.underPar() ? " §6" + callout : ""));
            }
            int streak = card.getBasketStreak();
            boolean milestone = streak == 3 || streak == 5 || streak == 8;
            if (milestone) play.message("§6" + event.getPlayer().getName() + "'s side has sunk " + streak + " holes in a row!");
            for (UUID id : scoringTeam.getTeamMembers()) {
                org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(id);
                if (player == null) continue;
                String subtitle = streak >= 2 ? "§6" + streak + " baskets in a row"
                        : "§fHole " + (event.getHoleNumber() + 1) + " complete";
                player.sendTitle("§a" + callout, subtitle, 5, 35, 10);
                if (milestone || basket.ace())
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, .6f, 1.4f);
            }
        }
    }

    private static String basketCallout(ScoreCard.Basket basket) {
        if (basket.ace()) return "Ace!";
        int difference = basket.strokes() - basket.par();
        if (difference <= -3) return "Albatross!";
        if (difference == -2) return "Eagle!";
        if (difference == -1) return "Birdie!";
        return "Basket!";
    }

    private boolean handleScoring(GoalScoreEvent event, Team team) {
        int holeNumber = event.getHoleNumber();
        GameRound round = event.getRound();
        ScoreCard scoreCard = round.getScoreCardByTeam(team);
        Integer par = round.getCourse().holePars().get(holeNumber);
        if (scoreCard == null || par == null || scoreCard.getHoleScores().containsKey(holeNumber)) {
            return false;
        }
        if (round instanceof SingleThrowRound single && (single.hasFinishedHole(team) || scoreCard.getCurrentStrokes() != 1))
            return false;

        scoreCard.recordBasket(holeNumber, scoreCard.getCurrentStrokes(), par,
                event.getPlayer().getUniqueId(), event.getDistance());
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
