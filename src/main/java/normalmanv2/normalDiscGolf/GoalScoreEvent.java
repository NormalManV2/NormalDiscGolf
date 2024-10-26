package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.impl.round.GameRound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class GoalScoreEvent extends Event {

    public static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final int holeNumber;
    private final GameRound round;

    public GoalScoreEvent(Player player, int holeNumber, GameRound round) {
        this.player = player;
        this.holeNumber = holeNumber;
        this.round = round;
    }

    public Player getPlayer() {
        return this.player;
    }

    public int getHoleNumber() {
        return this.holeNumber;
    }

    public GameRound getRound() {
        return this.round;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
