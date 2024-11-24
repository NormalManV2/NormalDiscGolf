package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.team.TeamImpl;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Round implements GameRound {
    private RoundState roundState;
    private final List<TeamImpl> teams;
    private final Map<TeamImpl, ScoreCard> scoreCards;
    private boolean roundOver;
    private final boolean isTournamentRound;
    private final Plugin plugin;
    private final PlayerDataManager playerDataManager;
    private final CourseImpl courseImpl;
    private final List<Integer> holes;
    private int holeIndex;
    private BukkitTask gameTask;
    private final Random random = new Random();
    private TeamImpl currentTeamTurn;

    public Round(Plugin plugin, PlayerDataManager playerDataManager, CourseImpl courseImpl, boolean isTournamentRound) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.courseImpl = courseImpl;
        this.teams = new ArrayList<>();
        this.scoreCards = new HashMap<>();
        this.isTournamentRound = isTournamentRound;
        this.holes = new ArrayList<>();
    }

    @Override
    public RoundState getRoundState() {
        return this.roundState;
    }

    @Override
    public void setRoundState(RoundState roundState) {
        this.roundState = roundState;
    }

    @Override
    public void startRound() {
        Location startingLocation = this.courseImpl.getStartingLocation();
        this.roundOver = false;
        this.holeIndex = 0;

        for (int i = 0; i <= this.courseImpl.getHoles(); i++) {
            this.holes.add(i);
        }
        for (TeamImpl teamImpl : teams) {
            this.scoreCards.put(teamImpl, new ScoreCard());

            for (UUID playerId : teamImpl.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    teamImpl.removePlayer(playerId);
                    if (teamImpl.getTeamMembers().isEmpty()) {
                        this.teams.remove(teamImpl);
                        this.scoreCards.remove(teamImpl);
                    }
                    continue;
                }
                player.teleport(startingLocation);
            }

            this.currentTeamTurn = this.teams.get(random.nextInt(this.teams.size()));
        }

        this.gameTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (TeamImpl teamImpl : teams) {
                for (UUID playerId : teamImpl.getTeamMembers()) {
                    Player player = Bukkit.getPlayer(playerId);
                    if (player == null) {
                        teamImpl.removePlayer(playerId);
                        if (teamImpl.getTeamMembers().isEmpty()) {
                            this.teams.remove(teamImpl);
                            this.scoreCards.remove(teamImpl);
                        }
                    }
                }
            }
            System.out.println(this + " Round Currently Running");
        }, 0, 100);
    }

    @Override
    public void endRound() {
        this.handleRoundEnd();
    }

    @Override
    public void cancelRound() {
        this.dispose();
    }

    @Override
    public void addTeam(Team team) {
        if (!(team instanceof TeamImpl)) {
            throw new IllegalArgumentException("Team must extend TeamImpl!");
        }
        this.teams.add((TeamImpl) team);
    }

    @Override
    public void removeTeam(Team team) {
        this.teams.remove((TeamImpl) team);
    }

    @Override
    public void handleStroke(UUID playerId, String technique, DiscImpl discImpl) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }

        PlayerData playerData = playerDataManager.getDataByPlayer(playerId);
        PlayerSkills skills = playerData.getSkills();

        for (TeamImpl teamImpl : this.teams) {
            if (teamImpl.getTeamMembers().contains(playerId)) {
                ScoreCard scoreCard = this.scoreCards.get(teamImpl);
                scoreCard.trackStroke();
            }
        }

        discImpl.handleThrow(player, skills, technique, player.getFacing());
    }

    @Override
    public void setTurn(Team team) {
        if (team instanceof TeamImpl) {
            this.currentTeamTurn = (TeamImpl) team;
        }
    }

    @Override
    public boolean isTurn(Team team) {
        return team.equals(this.currentTeamTurn);
    }

    @Override
    public void nextTurn() {
        if (this.currentTeamTurn == null || this.teams.isEmpty()) return;

        int currentIndex = this.teams.indexOf(this.currentTeamTurn);
        int nextIndex = (currentIndex + 1) % this.teams.size();
        this.currentTeamTurn = this.teams.get(nextIndex);
    }

    @Override
    public void nextHole() {
        this.holeIndex++;
    }

    @Override
    public boolean isRoundOver() {
        return this.roundOver;
    }

    @Override
    public List<Team> getTeams() {
        return Collections.unmodifiableList(this.teams);
    }

    @Override
    public CourseImpl getCourse() {
        return this.courseImpl;
    }

    @Override
    public boolean isTournamentRound() {
        return this.isTournamentRound;
    }

    @Override
    public int getCurrentHoleNumber() {
        return holeIndex;
    }

    private void handleRoundEnd() {
        this.roundOver = true;
        for (TeamImpl teamImpl : this.teams) {
            ScoreCard scoreCard = this.scoreCards.get(teamImpl);
            if (scoreCard == null) {
                return;
            }

            for (UUID playerId : teamImpl.getTeamMembers()) {
                PlayerData playerData = playerDataManager.getDataByPlayer(playerId);
                PDGARating rating = playerData.getRating();
                rating.handleRoundEnd(scoreCard.getTotalScore());
                rating.updateRating(courseImpl.getDifficulty());
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

    public Map<TeamImpl, ScoreCard> getScoreCards() {
        return this.scoreCards;
    }

    private void dispose() {
        this.teams.clear();
        this.scoreCards.clear();
        this.gameTask.cancel();
        this.gameTask = null;
        this.currentTeamTurn = null;
    }

}
