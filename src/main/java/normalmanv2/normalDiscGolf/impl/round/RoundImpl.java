package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.round.*;
import normalmanv2.normalDiscGolf.api.round.delegate.lifecycle.DelegateRoundLifecycle;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundScoreCardManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundStrokeManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTeamManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTurnManager;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundLifecycle;
import normalmanv2.normalDiscGolf.api.round.manager.RoundScoreCardManager;
import normalmanv2.normalDiscGolf.api.round.manager.RoundStrokeManager;
import normalmanv2.normalDiscGolf.api.round.manager.RoundTeamManager;
import normalmanv2.normalDiscGolf.api.round.manager.RoundTurnManager;
import normalmanv2.normalDiscGolf.api.round.settings.RoundSettings;
import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;


public class RoundImpl implements GameRound {

    private final String id;
    private final CourseImpl courseImpl;

    private final DefaultRoundSettings roundSettings;
    private final DefaultRoundTurnManager roundTurnManager;
    private final DefaultRoundTeamManager roundTeamManager;
    private final DefaultRoundLifecycle roundLifecycle;
    private final DefaultRoundStrokeManager strokeManager;
    private final DefaultRoundScoreCardManager scoreCardManager;

    private BukkitTask gameTask;
    private boolean isWired = false;

    public RoundImpl(
            CourseImpl courseImpl,
            String id,
            DefaultRoundSettings roundSettings) {

        this.courseImpl = courseImpl;
        this.id = id;

        this.roundSettings = roundSettings;
        this.roundTurnManager = (DefaultRoundTurnManager) DelegateRoundTurnManager.createDefault();
        this.roundTeamManager = (DefaultRoundTeamManager) DelegateRoundTeamManager.createDefault();
        this.roundLifecycle = (DefaultRoundLifecycle) DelegateRoundLifecycle.createDefault();
        this.strokeManager = (DefaultRoundStrokeManager) DelegateRoundStrokeManager.createDefault();
        this.scoreCardManager = (DefaultRoundScoreCardManager) DelegateRoundScoreCardManager.createDefault();
    }

    @Override
    public BukkitTask getGameTask() {
        return this.gameTask;
    }

    @Override
    public void setGameTask(BukkitTask gameTask) {
        this.gameTask = gameTask;
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

    @Override
    public void dispose() {
        this.gameTask.cancel();
        this.gameTask = null;
        this.scoreCardManager.dispose();
        this.roundTeamManager.dispose();
        this.roundTurnManager.dispose();
    }

    @Override
    public void wire(GameRound round, Plugin plugin) {
        this.initWire(plugin);
    }

    private void initWire(Plugin plugin) {

        if (this.isWired) {
            throw new IllegalStateException("This round is already wired!");
        }

        this.roundTurnManager.wire(this, null);
        this.roundTeamManager.wire(this, null);
        this.roundLifecycle.wire(this, plugin);
        this.strokeManager.wire(this, null);
        this.isWired = true;
    }

    @Override
    public void unwire() {
        if (!this.isWired) {
            throw new IllegalStateException("This round is not wired!");
        }

        this.roundTurnManager.unwire();
        this.roundTeamManager.unwire();
        this.roundLifecycle.unwire();
        this.strokeManager.unwire();
        this.isWired = false;
    }

    @Override
    public boolean isWired() {
        return this.isWired;
    }

}
