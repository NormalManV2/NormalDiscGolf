package normalmanv2.normalDiscGolf.impl.round;

import normalmanv2.normalDiscGolf.api.GameRound;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RoundHandler {

    private final Map<RoundState, Set<GameRound>> activeRounds;

    public RoundHandler() {
        this.activeRounds = new HashMap<>();
    }

    public boolean startRound(GameRound round) {
        this.activeRounds.computeIfAbsent(RoundState.START, k -> new HashSet<>()).add(round);
        round.startRound();
        return round.getTask() == null;
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
                System.out.println(round + "Has been ended!");
            }
        }
    }

    public List<GameRound> getActiveRounds() {
        return new ArrayList<>(this.activeRounds.getOrDefault(RoundState.START, Set.of()));
    }
}
