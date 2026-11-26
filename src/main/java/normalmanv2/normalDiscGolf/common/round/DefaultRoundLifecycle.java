package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundLifecycle;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundResult;
import normalmanv2.normalDiscGolf.api.round.delegate.lifecycle.DelegateRoundLifecycle;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.round.RoundPresentation;
import normalmanv2.normalDiscGolf.impl.round.RoundState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;

public class DefaultRoundLifecycle implements DelegateRoundLifecycle {
    private boolean started = false;
    private boolean ended = false;
    private RoundState state;

    private GameRound round;
    private Plugin plugin;
    private boolean isWired = false;

    @Override
    public RoundLifecycle getRoundLifecycle() {
        return this;
    }

    @Override
    public void wire(@NotNull GameRound round, @NotNull Plugin plugin) {

        if (this.isWired) {
            throw new IllegalStateException("This lifecycle is already wired!");
        }

        this.round = round;
        this.plugin = plugin;
        this.isWired = true;
    }

    @Override
    public void unwire() {
        if (!this.isWired) {
            throw new IllegalStateException("This lifecycle is not wired!");
        }

        this.round = null;
        this.plugin = null;
        this.isWired = false;
    }

    @Override
    public boolean isWired() {
        return this.isWired;
    }

    @Override
    public RoundResult start() {
        if (this.started) return RoundResult.ALREADY_STARTED;
        if (this.round.getTeams().isEmpty()) return RoundResult.NOT_ENOUGH_TEAMS;

        this.started = true;
        this.state = RoundState.START;

        World world = Bukkit.getWorld(this.round.getId());
        if (world == null) {
            throw new IllegalStateException("World " + this.round.getId() + " does not exist");
        }

        NDGManager.getInstance().getPlayerSessionManager().beginRound(this.round);

        for (Team team : this.round.getTeams()) {
            this.round.getRoundScoreCardManager().addScoreCard(team, new ScoreCard());
            Set<UUID> missingPlayers = new HashSet<>();

            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    missingPlayers.add(playerId);
                    continue;
                }

                player.setGameMode(org.bukkit.GameMode.ADVENTURE);
                player.setAllowFlight(false);
                player.setInvulnerable(true);
                player.setCollidable(false);
                player.setFoodLevel(20);
                player.setFireTicks(0);
                normalmanv2.normalDiscGolf.impl.listener.RoundPlayerListener.giveKit(player);
            }

            missingPlayers.forEach(team::removePlayer);
            if (team.getTeamMembers().isEmpty()) {
                this.round.getRoundTeamManager().removeTeam(team);
                this.round.getRoundScoreCardManager().removeScoreCard(team);
            }
        }

        this.round.getRoundTurnManager().selectRandomTeamTurn();
        ((normalmanv2.normalDiscGolf.impl.round.RoundImpl)this.round).play().startHole();
        for (Team team : this.round.getTeams()) for (UUID id : team.getTeamMembers()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) RoundPresentation.teleportToCurrentTee(this.plugin, player, this.round, true);
        }
        this.round.setGameTask(Bukkit.getScheduler().runTaskTimer(this.plugin,
                () -> ((normalmanv2.normalDiscGolf.impl.round.RoundImpl)this.round).play().tick(), 20, 20));
        return RoundResult.SUCCESS;
    }

    @Override
    public RoundResult end() {
        if (!this.started || this.ended) {
            return RoundResult.INVALID;
        }
        this.ended = true;
        this.state = RoundState.END;

        try {
            this.updatePlayerRatings();
            this.sendRoundResults();
        } finally {
            this.restoreRoundPlayers();
            this.round.dispose();
        }
        return RoundResult.ENDED;
    }

    private void updatePlayerRatings() {
        for (Team team : this.round.getTeams()) {
            ScoreCard scoreCard = this.round.getScoreCardByTeam(team);
            if (scoreCard == null) {
                continue;
            }

            for (UUID playerId : team.getTeamMembers()) {
                PlayerData playerData = this.round.getPlayerDataManager().getDataByPlayer(playerId);
                if (playerData == null) {
                    continue;
                }

                PDGARating rating = playerData.getRating();
                if (scoreCard.getHoleScores().size() != this.round.getCourse().holes()) continue;
                rating.handleRoundEnd(scoreCard.getTotalScore() * 18.0 / this.round.getCourse().holes());
                rating.updateRating(this.round.getSettings().division().getDifficulty());
                this.round.getPlayerDataManager().savePlayerData(playerId);
            }
        }
    }

    @Override
    public boolean isOver() {
        return this.ended || this.state == RoundState.END || this.state == RoundState.CANCEL;
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
        try {
            this.sendRoundMessage(ChatColor.RED + "Round canceled.");
        } finally {
            this.restoreRoundPlayers();
            this.round.dispose();
        }
        return RoundResult.CANCELED;
    }

    private void restoreRoundPlayers() {
        RoundPresentation.cancelRound(this.round);
        NDGManager.getInstance().getPlayerSessionManager().restoreRoundSessions(this.round);
    }

    private void sendRoundResults() {
        List<Map.Entry<Team, ScoreCard>> standings = new ArrayList<>(this.round.getScoreCards().entrySet());
        standings.sort(Comparator
                .comparingInt((Map.Entry<Team, ScoreCard> entry) -> entry.getValue().getTotalScore())
                .thenComparingInt(entry -> entry.getValue().getTotalStrokes())
                .thenComparing(entry -> this.formatTeamMembers(entry.getKey())));

        for (Team team : this.round.getTeams()) {
            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    continue;
                }

                PlayerData playerData = this.round.getPlayerDataManager().getDataByPlayer(playerId);
                player.sendMessage(ChatColor.GREEN + "Round complete!");
                player.sendMessage(ChatColor.GRAY + "Final standings:");

                for (int index = 0; index < standings.size(); index++) {
                    Map.Entry<Team, ScoreCard> entry = standings.get(index);
                    ScoreCard scoreCard = entry.getValue();
                    player.sendMessage(ChatColor.GRAY.toString()
                            + (index + 1)
                            + ". "
                            + this.formatTeamMembers(entry.getKey())
                            + " - "
                            + this.formatRelativeScore(scoreCard.getTotalScore())
                            + " ("
                            + scoreCard.getTotalStrokes()
                            + " strokes)");
                }

                if (playerData != null) {
                    PDGARating rating = playerData.getRating();
                    player.sendMessage(ChatColor.GRAY
                            + "Rating: "
                            + rating.getRating()
                            + " after "
                            + rating.getTotalRounds()
                            + " rounds.");
                }
            }
        }
    }

    private void sendRoundMessage(String message) {
        for (Team team : this.round.getTeams()) {
            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(message);
                }
            }
        }
    }

    private String formatTeamMembers(Team team) {
        StringJoiner joiner = new StringJoiner(", ");
        for (UUID playerId : team.getTeamMembers()) {
            joiner.add(this.formatPlayerName(playerId));
        }
        return joiner.toString();
    }

    private String formatPlayerName(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return player.getName();
        }

        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
        return playerName != null ? playerName : playerId.toString();
    }

    private String formatRelativeScore(int score) {
        if (score > 0) {
            return "+" + score;
        }
        return String.valueOf(score);
    }
}
