package normalmanv2.normalDiscGolf.impl.event;

import normalmanv2.normalDiscGolf.common.queue.RoundQueue;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class PlayerQueueRoundEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String roundType;
    private final List<RoundQueue> roundQueue;

    public PlayerQueueRoundEvent(UUID playerId, List<RoundQueue> roundQueue, String roundType) {
        this.playerId = playerId;
        this.roundQueue = roundQueue;
        this.roundType = roundType;
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public String getRoundType() {
        return this.roundType;
    }

    public List<RoundQueue> getRoundQueue() {
        return Collections.unmodifiableList(this.roundQueue);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
