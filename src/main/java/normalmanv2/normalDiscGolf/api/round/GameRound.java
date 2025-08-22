package normalmanv2.normalDiscGolf.api.round;

import normalmanv2.normalDiscGolf.api.course.Course;
import normalmanv2.normalDiscGolf.api.round.delegate.lifecycle.DelegateRoundLifecycle;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundScoreCardManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundStrokeManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTeamManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTurnManager;
import normalmanv2.normalDiscGolf.api.round.delegate.settings.DelegateRoundSettings;
import org.bukkit.scheduler.BukkitTask;

public interface GameRound extends DelegateRoundLifecycle, DelegateRoundTeamManager, DelegateRoundTurnManager, DelegateRoundSettings, DelegateRoundScoreCardManager, DelegateRoundStrokeManager {

    String getId();

    Course getCourse();

    BukkitTask getGameTask();

    void setGameTask(BukkitTask gameTask);

    void dispose();
}