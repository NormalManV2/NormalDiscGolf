package normalmanv2.normalDiscGolf.impl.manager.round.lifecycle;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.api.division.Division;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.manager.round.queue.RoundQueueManager;
import normalmanv2.normalDiscGolf.impl.round.DoublesRound;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.round.RoundState;
import normalmanv2.normalDiscGolf.impl.util.Constants;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class RoundHandler {

    private final Set<GameRound> activeRounds;
    private final NormalDiscGolf plugin;


    public RoundHandler(NormalDiscGolf normalDiscGolf) {
        this.activeRounds = new HashSet<>();
        this.plugin = normalDiscGolf;
    }

    public void startQueueTask(RoundQueueManager queueManager) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            queueManager.tickPlayerQueue();
            queueManager.tickRoundQueue();
        }, 0, 300);
    }

    public void startRound(GameRound round) {
        round.setRoundState(RoundState.START);
        this.activeRounds.add(round);
        round.startRound();
    }

    public void endRound(GameRound round) {
        round.setRoundState(RoundState.END);
        this.cleanupEndedRounds();
    }

    public void cancelRound(GameRound round) {
        round.setRoundState(RoundState.CANCEL);
        this.cleanupEndedRounds();
    }

    public GameRound createRound(Division division, String roundType, boolean isTournamentRound) {
        World world = Bukkit.getWorld("NDGCourses");

        if (world == null) throw new RuntimeException("World is null!");

        Supplier<GameRound> roundSupplier = switch (roundType.toLowerCase()) {
            case "ffa" -> () -> createFFARound(isTournamentRound, division, world);
            case "doubles" -> () -> createDoublesRound(isTournamentRound, division, world);
            default -> throw new IllegalArgumentException("Unknown round type: " + roundType);
        };
        return roundSupplier.get();
    }

    private GameRound createFFARound(boolean isTournamentRound, Division division, World world) {
        return new FFARound(
                plugin,
                NDGManager.getInstance().getPlayerDataManager(),
                NDGManager.getInstance().courseCreator().create(division, world),
                isTournamentRound,
                "FFA_ROUND." + UUID.randomUUID(),
                Constants.FFA_ROUND_MAX_PLAYERS);
    }

    private GameRound createDoublesRound(boolean isTournamentRound, Division division, World world) {
        return new DoublesRound(
                plugin,
                NDGManager.getInstance().getPlayerDataManager(),
                NDGManager.getInstance().courseCreator().create(division, world),
                isTournamentRound,
                "DOUBLES_ROUND." + UUID.randomUUID(),
                Constants.DOUBLES_ROUND_MAX_TEAMS);
    }

    private void cleanupEndedRounds() {
        final Iterator<GameRound> activeRoundsIterator = activeRounds.iterator();
        while (activeRoundsIterator.hasNext()) {
            final GameRound gameRound = activeRoundsIterator.next();
            final RoundState roundState = gameRound.getRoundState();
            if (roundState != RoundState.END && roundState != RoundState.CANCEL) {
                continue;
            }

            activeRoundsIterator.remove();
            gameRound.endRound();
            System.out.println(gameRound + " Has been ended!");
        }
    }

    public List<GameRound> getActiveRounds() {
        return new ArrayList<>(this.activeRounds);
    }
}
