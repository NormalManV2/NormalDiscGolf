package normalmanv2.normalDiscGolf.impl.manager;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.common.queue.RoundQueue;
import normalmanv2.normalDiscGolf.impl.round.RoundState;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class RoundHandler {

    private final Set<GameRound> activeRounds;
    private final QueueManager queueManager;
    private final NormalDiscGolf normalDiscGolf;

    public RoundHandler(QueueManager queueManager, NormalDiscGolf normalDiscGolf) {
        this.activeRounds = new HashSet<>();
        this.queueManager = queueManager;
        this.normalDiscGolf = normalDiscGolf;
        this.startQueueTask();
    }

    private void startQueueTask() {
        Bukkit.getScheduler().runTaskTimer(normalDiscGolf, () -> {
            for (RoundQueue queue : this.queueManager.getQueuedRounds()) {
                this.startRound(queue.round());
            }
        }, 0, 6000);
    }

    public void startRound(GameRound round) {
        round.setRoundState(RoundState.START);
        this.activeRounds.add(round);
        round.startRound();
        this.dequeueRound(round);
    }

    public void endRound(GameRound round) {
        round.setRoundState(RoundState.END);
        this.cleanupEndedRounds();
    }

    public void cancelRound(GameRound round) {
        round.setRoundState(RoundState.CANCEL);
        this.cleanupEndedRounds();
    }

    public void queueRound(GameRound round) {
        this.queueManager.queueRound(round);
    }

    public void dequeueRound(GameRound round) {
        this.queueManager.removeRound(round.getId());
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
            System.out.println(gameRound + "Has been ended!");
        }
    }

    public List<GameRound> getActiveRounds() {
        return new ArrayList<>(this.activeRounds);
    }
}
