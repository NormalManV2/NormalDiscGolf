package normalmanv2.normalDiscGolf.round;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class RoundHandler {

    private final ConcurrentMap<RoundState, Set<GameRound>> activeRounds;

    public RoundHandler() {
        this.activeRounds = new ConcurrentHashMap<>();
    }

    public void startRound(GameRound round) {
        this.activeRounds.computeIfAbsent(RoundState.START, k -> new HashSet<>()).add(round);
        round.startRound();
    }

    public void endRound(GameRound round) {
        this.activeRounds.computeIfAbsent(RoundState.END, k -> new HashSet<>()).add(round);
        this.cleanupEndedRounds();
    }

    public void cancelRound(GameRound round) {
        this.activeRounds.computeIfAbsent(RoundState.CANCEL, k -> new HashSet<>()).add(round);
        this.cleanupEndedRounds();
    }

    private void cleanupEndedRounds() {
        for (RoundState state : Set.of(RoundState.END, RoundState.CANCEL)) {
            Set<GameRound> rounds = this.activeRounds.get(state);
            if (rounds == null) {
                return;
            }
            Iterator<GameRound> iterator = rounds.iterator();
            while (iterator.hasNext()) {
                GameRound round = iterator.next();
                round.endRound();
                iterator.remove();
            }
        }
    }

    public List<GameRound> getActiveRounds() {
        return new ArrayList<>(this.activeRounds.getOrDefault(RoundState.START, Set.of()));
    }
}
