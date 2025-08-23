package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import org.bukkit.plugin.Plugin;

public class DoublesRound extends RoundImpl {

    public DoublesRound(
            CourseImpl courseImpl,
            String id,
            DefaultRoundSettings settings,
            DefaultRoundTurnManager turnManager,
            DefaultRoundTeamManager teamManager,
            DefaultRoundLifecycle lifecycle,
            DefaultRoundStrokeManager strokeManager,
            DefaultRoundScoreCardManager scoreCardManager) {

        super(courseImpl, id, settings, turnManager, teamManager, lifecycle, strokeManager, scoreCardManager);

    }
}
