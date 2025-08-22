package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.round.GameRound;
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

    private final GameRound round;
    private final NormalDiscGolfPlugin plugin;

    public DefaultRoundLifecycle(GameRound round, NormalDiscGolfPlugin plugin) {
        this.round = round;
        this.plugin = plugin;
    }

    @Override
    public RoundLifecycle getRoundLifecycle() {
        return this;
    }

    @Override
    public RoundResult start() {
        if (this.started) return RoundResult.ALREADY_STARTED;
        this.started = true;
        this.state = RoundState.START;

        for (Team team : this.round.getTeams()) {
            this.round.getRoundScoreCardManager().addScoreCard(team, new ScoreCard());

            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    team.removePlayer(playerId);
                    if (team.getTeamMembers().isEmpty()) {
                        this.round.getRoundTeamManager().removeTeam(team);
                        this.round.getRoundScoreCardManager().removeScoreCard(team);
                    }
                    continue;
                }

                Bukkit.getScheduler().runTaskLater(this.plugin, () -> {

                    if (Bukkit.getWorld(this.round.getId()) == null) {
                        throw new RuntimeException("World " + this.round.getId() + " does not exist");
                    }

                    Location startingLocation = Bukkit.getWorld(this.round.getId()).getSpawnLocation();

                    player.teleport(startingLocation);
                }, 20);
            }

            this.round.getRoundTurnManager().selectRandomTeamTurn();
        }

        this.round.setGameTask(Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            this.round.getRoundTeamManager().tick();
            System.out.println(this.round.getId() + " Round Currently Running");
        }, 0, 100));
        return RoundResult.SUCCESS;
    }

    @Override
    public RoundResult end() {
        if (!this.started || this.ended) {
            return RoundResult.INVALID;
        }
        this.ended = true;
        this.state = RoundState.END;

        for (Team team : this.round.getTeams()) {
            ScoreCard scoreCard = this.round.getScoreCardByTeam(team);
            if (scoreCard == null) {
                continue;
            }

            for (UUID playerId : team.getTeamMembers()) {
                PlayerData playerData = this.round.getPlayerDataManager().getDataByPlayer(playerId);
                PDGARating rating = playerData.getRating();
                rating.handleRoundEnd(scoreCard.getTotalScore());
                rating.updateRating(this.round.getSettings().division().getDifficulty());
                System.out.println(scoreCard.getTotalStrokes());
                System.out.println(scoreCard.getTotalScore());
                System.out.println(rating.getRating());
                System.out.println(rating.getAverageScore());
                System.out.println(rating.getDivision());
                System.out.println(rating.getTotalRounds());
            }
        }
        this.round.dispose();
        return RoundResult.ENDED;
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