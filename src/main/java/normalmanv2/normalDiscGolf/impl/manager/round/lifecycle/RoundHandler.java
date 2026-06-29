package normalmanv2.normalDiscGolf.impl.manager.round.lifecycle;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.lifecycle.RoundResult;
import normalmanv2.normalDiscGolf.common.round.*;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.impl.round.DoublesRound;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.round.RoundState;
import normalmanv2.normalDiscGolf.impl.course.grid.CourseGridWorldCreator;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.*;
import java.util.function.Supplier;

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
        if (!round.isWired()) {
            round.wire(round, this.plugin);
        }

        round.setState(RoundState.START);
        this.activeRounds.add(round);
        RoundResult result = round.start();
        if (result != RoundResult.SUCCESS && result != RoundResult.ALREADY_STARTED) {
            this.activeRounds.remove(round);
        }
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
            DefaultRoundSettings settings) {

        World world = Bukkit.createWorld(new CourseGridWorldCreator(courseName, course, settings.division()));

        if (world == null) throw new RuntimeException("World is null!");

        Supplier<GameRound> roundSupplier = switch (roundFormat.toLowerCase()) {
            case "ffa" -> () -> createFFARound(course, courseName, settings);
            case "doubles" -> () -> createDoublesRound(course, courseName, settings);
            default -> throw new IllegalArgumentException("Unknown round type: " + roundFormat);
        };

        GameRound round = roundSupplier.get();
        round.wire(round, this.plugin);
        return round;
    }

    private GameRound createFFARound(
            CourseImpl course,
            String id,
            DefaultRoundSettings settings) {
        return new FFARound(
                course,
                id,
                settings);
    }

    private GameRound createDoublesRound(
            CourseImpl course,
            String id,
            DefaultRoundSettings settings) {
        return new DoublesRound(
                course,
                id,
                settings);
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
            gameRound.end();
        }
    }

    public List<GameRound> getActiveRounds() {
        return new ArrayList<>(this.activeRounds);
    }
}
