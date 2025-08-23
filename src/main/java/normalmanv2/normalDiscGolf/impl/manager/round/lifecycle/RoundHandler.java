package normalmanv2.normalDiscGolf.impl.manager.round.lifecycle;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.round.settings.RoundType;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseCreator;
import normalmanv2.normalDiscGolf.impl.course.CourseGrid;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.round.DoublesRound;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.round.RoundState;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import normalmanv2.normalDiscGolf.impl.course.world.CourseGridWorldCreator;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.*;
import java.util.function.Supplier;

import static normalmanv2.normalDiscGolf.impl.util.Constants.*;

public class RoundHandler {

    private final Set<GameRound> activeRounds;
    private final NormalDiscGolfPlugin plugin;

    public RoundHandler(NormalDiscGolfPlugin normalDiscGolfPlugin) {
        this.activeRounds = new HashSet<>();
        this.plugin = normalDiscGolfPlugin;
        this.startQueueTask();
    }

    public void startQueueTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            NDGManager.getInstance().getRoundQueueManager().tickPlayerQueue();
            NDGManager.getInstance().getRoundQueueManager().tickRoundQueue();
        }, 0, 20);
    }

    public void startRound(GameRound round) {
        round.setState(RoundState.START);
        this.activeRounds.add(round);
        round.start();
    }

    public void endRound(GameRound round) {
        round.setState(RoundState.END);
        this.cleanupEndedRounds();
    }

    public void cancelRound(GameRound round) {
        round.setState(RoundState.CANCEL);
        this.cleanupEndedRounds();
    }

    public GameRound createRound(
            String roundFormat,
            CourseImpl course,
            String courseName,
            DefaultRoundSettings settings,
            DefaultRoundTurnManager turnManager,
            DefaultRoundTeamManager teamManager,
            DefaultRoundLifecycle lifecycle,
            DefaultRoundStrokeManager strokeManager,
            DefaultRoundScoreCardManager scoreCardManager) {

        World world = Bukkit.createWorld(new CourseGridWorldCreator(courseName, course, settings.division()));

        if (world == null) throw new RuntimeException("World is null!");

        Supplier<GameRound> roundSupplier = switch (roundFormat.toLowerCase()) {
            case "ffa" -> () -> createFFARound(course, courseName, settings, turnManager, teamManager, lifecycle, strokeManager, scoreCardManager);
            case "doubles" -> () -> createDoublesRound(course, courseName, settings, turnManager, teamManager, lifecycle, strokeManager, scoreCardManager);
            default -> throw new IllegalArgumentException("Unknown round type: " + roundFormat);
        };
        return roundSupplier.get();
    }

    private GameRound createFFARound(
            CourseImpl course,
            String id,
            DefaultRoundSettings settings,
            DefaultRoundTurnManager turnManager,
            DefaultRoundTeamManager teamManager,
            DefaultRoundLifecycle lifecycle,
            DefaultRoundStrokeManager strokeManager,
            DefaultRoundScoreCardManager scoreCardManager) {
        return new FFARound(
                course,
                id,
                settings,
                turnManager,
                teamManager,
                lifecycle,
                strokeManager,
                scoreCardManager);
    }

    private GameRound createDoublesRound(
            CourseImpl course,
            String id,
            DefaultRoundSettings settings,
            DefaultRoundTurnManager turnManager,
            DefaultRoundTeamManager teamManager,
            DefaultRoundLifecycle lifecycle,
            DefaultRoundStrokeManager strokeManager,
            DefaultRoundScoreCardManager scoreCardManager) {
        return new DoublesRound(
                course,
                id,
                settings,
                turnManager,
                teamManager,
                lifecycle,
                strokeManager,
                scoreCardManager);
    }

    private void cleanupEndedRounds() {
        final Iterator<GameRound> activeRoundsIterator = activeRounds.iterator();
        while (activeRoundsIterator.hasNext()) {
            final GameRound gameRound = activeRoundsIterator.next();
            final RoundState roundState = gameRound.getState();
            if (roundState != RoundState.END && roundState != RoundState.CANCEL) {
                continue;
            }

            activeRoundsIterator.remove();
            System.out.println(gameRound.end().toString());
            System.out.println(gameRound + " Has been ended!");
        }
    }

    public List<GameRound> getActiveRounds() {
        return new ArrayList<>(this.activeRounds);
    }
}
