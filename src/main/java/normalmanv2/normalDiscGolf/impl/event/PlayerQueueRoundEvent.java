package normalmanv2.normalDiscGolf.impl.event;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PlayerQueueRoundEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String roundFormat;
    private final Set<GameRound> roundQueue;

    public PlayerQueueRoundEvent(UUID playerId, Set<GameRound> roundQueue, String roundFormat) {
        this.playerId = playerId;
        this.roundQueue = roundQueue;
        this.roundFormat = roundFormat;
        System.out.println("PlayerQueueRoundEvent: " + playerId + " roundQueue " + roundQueue + " roundType " + roundFormat);
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public String getRoundFormat() {
        return this.roundFormat;
    }

    public Set<GameRound> getRoundQueue() {
        return Collections.unmodifiableSet(this.roundQueue);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
