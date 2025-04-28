package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import org.bukkit.plugin.Plugin;

public class FFARound extends RoundImpl {

    public FFARound(Plugin plugin, PlayerDataManager playerDataManager, CourseImpl courseImpl, boolean isTournamentRound, String id, int maximumTeams) {
        super (plugin, playerDataManager, courseImpl, isTournamentRound, id, maximumTeams);
    }
}