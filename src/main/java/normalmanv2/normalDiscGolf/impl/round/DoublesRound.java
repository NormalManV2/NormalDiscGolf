package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public class DoublesRound extends RoundImpl {

    public DoublesRound(Plugin plugin, PlayerDataManager playerDataManager, CourseImpl courseImpl, boolean isTournamentRound, String id, int maximumTeams) {
        super(plugin, playerDataManager, courseImpl, isTournamentRound, id, maximumTeams);
    }

    @Override
    public void handleStroke(ThrowMechanic throwMechanic) {
        super.handleStroke(throwMechanic);
    }

}
