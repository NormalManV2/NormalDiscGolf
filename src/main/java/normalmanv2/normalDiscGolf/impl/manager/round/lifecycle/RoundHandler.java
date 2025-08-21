package normalmanv2.normalDiscGolf.impl.manager.round.lifecycle;

import normalmanv2.normalDiscGolf.NormalDiscGolfPlugin;
import normalmanv2.normalDiscGolf.common.division.Division;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.course.CourseCreator;
import normalmanv2.normalDiscGolf.impl.round.DoublesRound;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.round.RoundState;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import normalmanv2.normalDiscGolf.impl.course.world.CourseGridWorldCreator;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

    public GameRound createRound(Division division, String roundType, boolean isTournamentRound, boolean isPrivate, String courseName) {
        World world = Bukkit.createWorld(new CourseGridWorldCreator(courseName, new CourseCreator().create(DEFAULT_FFA_COURSE_SIZE, DEFAULT_COURSE_HOLES, courseName), division));

        if (world == null) throw new RuntimeException("World is null!");

        Supplier<GameRound> roundSupplier = switch (roundType.toLowerCase()) {
            case "ffa" -> () -> createFFARound(isTournamentRound, isPrivate, courseName);
            case "doubles" -> () -> createDoublesRound(isTournamentRound, isPrivate, courseName);
            default -> throw new IllegalArgumentException("Unknown round type: " + roundType);
        };
        return roundSupplier.get();
    }

    private GameRound createFFARound(boolean isTournamentRound, boolean isPrivate, String courseName) {
        return new FFARound(
                plugin,
                NDGManager.getInstance().courseCreator()
                        .setSize(DEFAULT_FFA_COURSE_SIZE)
                        .setDivision(DEFAULT_FFA_COURSE_DIVISION)
                        .setHoles(DEFAULT_COURSE_HOLES)
                        .setName(courseName)
                        .create(),
                isTournamentRound,
                courseName,
                Constants.FFA_ROUND_MAX_PLAYERS,
                isPrivate);
    }

    private GameRound createDoublesRound(boolean isTournamentRound, boolean isPrivate, String courseName) {
        return new DoublesRound(
                plugin,
                NDGManager.getInstance().courseCreator()
                        .setSize(DEFAULT_DUBS_COURSE_SIZE)
                        .setDivision(DEFAULT_DUBS_COURSE_DIVISION)
                        .setHoles(DEFAULT_COURSE_HOLES)
                        .setName(courseName)
                        .create(),
                isTournamentRound,
                "DOUBLES_ROUND." + UUID.randomUUID(),
                Constants.DOUBLES_ROUND_MAX_TEAMS,
                isPrivate);
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
            System.out.println(gameRound + " Has been ended!");
        }
    }

    public List<GameRound> getActiveRounds() {
        return new ArrayList<>(this.activeRounds);
    }
}
