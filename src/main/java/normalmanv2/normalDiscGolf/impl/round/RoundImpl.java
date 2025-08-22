package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.round.*;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundLifecycle;
import normalmanv2.normalDiscGolf.api.round.manager.RoundScoreCardManager;
import normalmanv2.normalDiscGolf.api.round.manager.RoundStrokeManager;
import normalmanv2.normalDiscGolf.api.round.manager.RoundTeamManager;
import normalmanv2.normalDiscGolf.api.round.manager.RoundTurnManager;
import normalmanv2.normalDiscGolf.api.round.settings.RoundSettings;
import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;


public class RoundImpl implements GameRound {

    private final String id;
    private final CourseImpl courseImpl;

    private final DefaultRoundSettings roundSettings;
    private final DefaultRoundTurnManager roundTurnManager;
    private final DefaultRoundTeamManager roundTeamManager;
    private final DefaultRoundLifecycle roundLifecycle;
    private final DefaultRoundStrokeManager strokeManager;
    private final DefaultRoundScoreCardManager scoreCardManager;

    public RoundImpl(
            CourseImpl courseImpl,
            String id,
            DefaultRoundSettings roundSettings,
            DefaultRoundTurnManager roundTurnManager,
            DefaultRoundTeamManager roundTeamManager,
            DefaultRoundLifecycle roundLifecycle,
            DefaultRoundStrokeManager strokeManager,
            DefaultRoundScoreCardManager scoreCardManager) {


        this.courseImpl = courseImpl;
        this.id = id;

        this.roundSettings = roundSettings;
        this.roundTurnManager = roundTurnManager;
        this.roundTeamManager = roundTeamManager;
        this.roundLifecycle = roundLifecycle;
        this.strokeManager = strokeManager;
        this.scoreCardManager = scoreCardManager;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public CourseImpl getCourse() {
        return this.courseImpl;
    }

    @Override
    public RoundScoreCardManager getRoundScoreCardManager() {
        return this.scoreCardManager;
    }

    @Override
    public RoundStrokeManager getRoundStrokeManager() {
        return this.strokeManager;
    }

    @Override
    public RoundLifecycle getRoundLifecycle() {
        return this.roundLifecycle;
    }

    @Override
    public RoundTeamManager getRoundTeamManager() {
        return this.roundTeamManager;
    }

    @Override
    public RoundTurnManager getRoundTurnManager() {
        return this.roundTurnManager;
    }

    @Override
    public RoundSettings getSettings() {
        return this.roundSettings;
    }
}
