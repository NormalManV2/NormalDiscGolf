package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.course.Course;
import normalmanv2.normalDiscGolf.api.disc.Disc;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.api.team.Team;
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
import java.util.UUID;

public class Round implements GameRound {

    private final List<Team> teams;
    private final Map<Team, ScoreCard> scoreCards;
    private boolean roundOver;
    private final boolean isTournamentRound;
    private final Plugin plugin;
    private final PlayerDataManager playerDataManager;
    private final Course course;
    private final List<Integer> holes;
    private int holeIndex;
    private BukkitTask gameTask;

    public Round(Plugin plugin, PlayerDataManager playerDataManager, Course course, boolean isTournamentRound) {
        this.plugin = plugin;
        this.playerDataManager = playerDataManager;
        this.course = course;
        this.teams = new ArrayList<>();
        this.scoreCards = new HashMap<>();
        this.isTournamentRound = isTournamentRound;
        this.holes = new ArrayList<>();
    }

    @Override
    public void startRound() {
        Location startingLocation = this.course.getStartingLocation();
        this.roundOver = false;

        for (int i = 0; i <= this.course.getHoles(); i++) {
            this.holes.add(i);
        }
        for (Team team : teams) {
            this.scoreCards.put(team, new ScoreCard());

            for (UUID playerId : team.getPlayers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    continue;
                }
                player.teleport(startingLocation);
            }
        }

        this.gameTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
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
        this.teams.add(team);
    }

    @Override
    public void removeTeam(Team team) {
        this.teams.remove(team);
    }

    @Override
    public void handleStroke(UUID playerId, String technique, Disc disc) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }

        PlayerData playerData = playerDataManager.getDataByPlayer(playerId);
        PlayerSkills skills = playerData.getSkills();

        for (Team team : this.teams) {
            if (team.getPlayers().contains(playerId)) {
                ScoreCard scoreCard = this.scoreCards.get(team);
                scoreCard.trackStroke();
            }
        }

        disc.handleThrow(player, skills, technique, player.getFacing());
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
    public Course getCourse() {
        return this.course;
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
        for (Team team : this.teams) {
            ScoreCard scoreCard = this.scoreCards.get(team);
            if (scoreCard == null) {
                return;
            }

            for (UUID playerId : team.getPlayers()) {
                PlayerData playerData = playerDataManager.getDataByPlayer(playerId);
                PDGARating rating = playerData.getRating();
                rating.handleRoundEnd(scoreCard.getTotalScore());
                rating.updateRating(course.getDifficulty());
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

    private void dispose() {
        this.teams.clear();
        this.scoreCards.clear();
        this.gameTask.cancel();
        this.gameTask = null;
    }

}
