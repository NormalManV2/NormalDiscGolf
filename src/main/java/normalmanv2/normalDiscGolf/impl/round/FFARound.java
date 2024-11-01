package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.impl.course.Course;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import org.bukkit.plugin.Plugin;

public class FFARound extends Round {

    public FFARound(Plugin plugin, PlayerDataManager playerDataManager, Course course, boolean isTournamentRound) {
        super (plugin, playerDataManager, course, isTournamentRound);
    }


}