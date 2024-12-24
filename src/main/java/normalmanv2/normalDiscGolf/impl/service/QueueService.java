package normalmanv2.normalDiscGolf.impl.service;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.common.queue.RoundQueue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class QueueService {

    private final Map<String, RoundQueue> roundQueue;

    public QueueService() {
        this.roundQueue = new HashMap<>();
    }

    public RoundQueue createQueue(GameRound gameRound) {
        RoundQueue round = new RoundQueue(gameRound.getId(), gameRound);
        this.roundQueue.put(gameRound.getId(), round);
        return round;
    }

    public RoundQueue getQueue(String roundId) {
        return this.roundQueue.get(roundId);
    }

    public void queueRound(RoundQueue round) {
        this.roundQueue.put(round.id(), round);
    }

    public void removeQueue(String roundId) {
        this.roundQueue.remove(roundId);
    }

    public Map<String, RoundQueue> getQueuedRounds() {
        return Collections.unmodifiableMap(this.roundQueue);
    }

    public boolean isQueued(String roundId) {
        return this.roundQueue.containsKey(roundId);
    }

}
