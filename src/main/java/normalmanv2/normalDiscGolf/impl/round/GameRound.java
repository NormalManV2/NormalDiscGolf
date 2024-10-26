package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.impl.course.Course;
import normalmanv2.normalDiscGolf.impl.disc.Disc;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.UUID;

public interface GameRound {

    void startRound();
    void endRound();
    void cancelRound();
    void addPlayer(UUID player);
    void removePlayer(UUID player);
    void handleStroke(UUID player, String technique, Disc disc);
    boolean isRoundOver();
    List<UUID> getPlayers();
    Course getCourse();
    BukkitTask getTask();
    boolean isTournamentRound();
}
