package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.GameRound;
import normalmanv2.normalDiscGolf.api.NDGApi;
import normalmanv2.normalDiscGolf.impl.course.Course;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.player.PlayerSkills;
import normalmanv2.normalDiscGolf.impl.disc.Disc;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.player.score.PDGARating;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FFARound implements GameRound {

    private final List<UUID> players;
    private final ConcurrentMap<UUID, ScoreCard> scoreCards;
    private boolean roundOver;
    private BukkitTask task;
    private final JavaPlugin plugin;
    private final PlayerDataManager playerDataManager;
    private final Course course;
    private final boolean isTournamentRound;
    private int holeNumber;

    public FFARound(JavaPlugin plugin, Course course, boolean isTournamentRound) {
        this.players = new ArrayList<>();
        this.scoreCards = new ConcurrentHashMap<>();
        this.roundOver = false;
        this.plugin = plugin;
        this.playerDataManager = NDGApi.getInstance().getPlayerDataManager();
        this.course = course;
        this.isTournamentRound = isTournamentRound;
    }

    @Override
    public void startRound() {
        this.holeNumber = 1;
        for (UUID uuid : players) {
            this.scoreCards.put(uuid, new ScoreCard());
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                // player.teleport(this.course.getStartingLocation());
            }
        }

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> System.out.println("FFA ROUND IN PROGRESS"), 0, 100);
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
    public void addPlayer(UUID player) {
        this.players.add(player);
    }

    @Override
    public void removePlayer(UUID player) {
        if (!this.players.contains(player)) {

            return;
        }
        this.players.remove(player);
    }

    @Override
    public void handleStroke(UUID playerId, String technique, Disc disc) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        PlayerData playerData = playerDataManager.getDataByPlayer(playerId);
        PlayerSkills skills = playerData.getSkills();
        ScoreCard scoreCard = scoreCards.get(player.getUniqueId());
        scoreCard.trackStroke();

        disc.handleThrow(player, skills, technique, player.getFacing());
    }

    @Override
    public boolean isRoundOver() {
        return this.roundOver;
    }

    @Override
    public boolean isTournamentRound() {
        return this.isTournamentRound;
    }

    @Override
    public List<UUID> getPlayers() {
        return Collections.unmodifiableList(this.players);
    }

    private void handleRoundEnd() {
        this.roundOver = true;
        for (UUID uuid : this.players) {
            ScoreCard scoreCard = this.scoreCards.get(uuid);
            if (scoreCard == null) {
                return;
            }
            PlayerData playerData = playerDataManager.getDataByPlayer(uuid);
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
        this.dispose();
    }

    @Override
    public Course getCourse() {
        return this.course;
    }

    @Override
    public BukkitTask getTask() {
        return this.task;
    }

    public ConcurrentMap<UUID, ScoreCard> getScoreCards() {
        return this.scoreCards;
    }

    private void dispose() {
        this.task.cancel();
        this.task = null;
        this.scoreCards.clear();
        this.players.clear();
    }
}