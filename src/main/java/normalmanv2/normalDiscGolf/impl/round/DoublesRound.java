package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.impl.course.Course;
import normalmanv2.normalDiscGolf.api.disc.Disc;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class DoublesRound extends Round {

    public DoublesRound(Plugin plugin, PlayerDataManager playerDataManager, Course course, boolean isTournamentRound) {
        super(plugin, playerDataManager, course, isTournamentRound);
    }

    @Override
    public void handleStroke(UUID playerId, String technique, Disc disc) {
        super.handleStroke(playerId, technique, disc);
    }
}
