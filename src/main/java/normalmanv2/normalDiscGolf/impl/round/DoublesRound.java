package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.GameRound;
import normalmanv2.normalDiscGolf.impl.course.Course;
import normalmanv2.normalDiscGolf.impl.disc.Disc;
import normalmanv2.normalDiscGolf.impl.player.score.ScoreCard;
import normalmanv2.normalDiscGolf.impl.team.Team;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DoublesRound implements GameRound {

    private final Set<Team> teams;
    private final Map<Team, ScoreCard> scoreCards;
    private final boolean isTournamentRound;

    public DoublesRound(boolean isTournamentRound) {
        this.isTournamentRound = isTournamentRound;
        this.teams = new HashSet<>();
        this.scoreCards = new HashMap<>();
    }

    @Override
    public void startRound() {

    }

    @Override
    public void endRound() {

    }

    @Override
    public void cancelRound() {

    }

    @Override
    public void addPlayer(UUID player) {

    }

    @Override
    public void removePlayer(UUID player) {

    }

    @Override
    public void handleStroke(UUID player, String technique, Disc disc) {

    }

    @Override
    public boolean isRoundOver() {
        return false;
    }

    @Override
    public List<UUID> getPlayers() {
        return List.of();
    }

    @Override
    public Course getCourse() {
        return null;
    }

    @Override
    public BukkitTask getTask() {
        return null;
    }

    @Override
    public boolean isTournamentRound() {
        return false;
    }
}
