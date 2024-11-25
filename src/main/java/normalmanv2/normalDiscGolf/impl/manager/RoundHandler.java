package normalmanv2.normalDiscGolf.impl.manager;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.impl.round.RoundState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class RoundHandler {

    private final Set<GameRound> activeRounds;

    public RoundHandler() {
        this.activeRounds = new HashSet<>();
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