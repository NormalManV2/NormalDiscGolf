package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundLifecycle;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundResult;
import normalmanv2.normalDiscGolf.api.round.delegate.lifecycle.DelegateRoundLifecycle;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.round.RoundState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DefaultRoundLifecycle implements DelegateRoundLifecycle {
    private boolean started = false;
    private boolean ended = false;
    private RoundState state;

    @Override
    public RoundLifecycle getRoundLifecycle() {
        return this;
    }

    @Override
    public RoundResult start() {
        if (this.started) return RoundResult.ALREADY_STARTED;
        this.started = true;
        this.state = RoundState.START;
        return RoundResult.SUCCESS;

        for (Team teamImpl : this.getTeams()) {
            this.scoreCards.put(teamImpl, new ScoreCard());

            for (UUID playerId : teamImpl.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    teamImpl.removePlayer(playerId);
                    if (teamImpl.getTeamMembers().isEmpty()) {
                        this.getTeams().remove(teamImpl);
                        this.scoreCards.remove(teamImpl);
                    }
                    continue;
                }


                Bukkit.getScheduler().runTaskLater(this.plugin, () -> {

                    if (Bukkit.getWorld(this.id) == null) {
                        throw new RuntimeException("World " + this.id + " does not exist");
                    }

                    Location startingLocation = Bukkit.getWorld(this.id).getSpawnLocation();


                    player.teleport(startingLocation);
                }, 20);
            }

            this.roundTurnManager.selectRandomTeamTurn();
        }

        this.gameTask = Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            this.roundTeamManager.tick();
            System.out.println(this.id + " Round Currently Running");
        }, 0, 100);
        return RoundResult.SUCCESS;

    }

    @Override
    public RoundResult end() {
        if (!this.started || this.ended) return RoundResult.INVALID;
        this.ended = true;
        this.state = RoundState.END;
        return RoundResult.ENDED;

        for (Team team : this.teams) {
            ScoreCard scoreCard = this.scoreCards.get(team);
            if (scoreCard == null) {
                continue;
            }

            for (UUID playerId : team.getTeamMembers()) {
                PlayerData playerData = playerDataManager.getDataByPlayer(playerId);
                PDGARating rating = playerData.getRating();
                rating.handleRoundEnd(scoreCard.getTotalScore());
                rating.updateRating(courseImpl.difficulty());
                System.out.println(scoreCard.getTotalStrokes());
                System.out.println(scoreCard.getTotalScore());
                System.out.println(rating.getRating());
                System.out.println(rating.getAverageScore());
                System.out.println(rating.getDivision());
                System.out.println(rating.getTotalRounds());
            }
        }
        this.dispose();

    }

    @Override
    public boolean isOver() {
        return this.ended;
    }

    @Override
    public RoundState getState() {
        return this.state;
    }

    @Override
    public void setState(RoundState state) {
        this.state = state;
    }

    @Override
    public RoundResult cancel() {
        if (!this.started) return RoundResult.INVALID;
        this.ended = true;
        this.state = RoundState.CANCEL;
        return RoundResult.CANCELED;
    }
}