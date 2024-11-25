package normalmanv2.normalDiscGolf.impl.event;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.List;
import java.util.UUID;

public class PlayerRoundQueueEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID playerId;
    private final String roundName;
    private final List<GameRound> roundQueue;

    public PlayerRoundQueueEvent(UUID playerId, List<GameRound> roundQueue, String roundName) {
        this.playerId = playerId;
        this.roundQueue = roundQueue;
        this.roundName = roundName;
    }

    public UUID getPlayerId() {
        return this.playerId;
    }

    public String getRoundName() {
        return this.roundName;
    }

    public List<GameRound> getRoundQueue() {
        return this.roundQueue;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
