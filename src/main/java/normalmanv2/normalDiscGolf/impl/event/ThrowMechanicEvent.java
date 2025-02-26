package normalmanv2.normalDiscGolf.impl.event;

import normalmanv2.normalDiscGolf.api.mechanic.ThrowMechanic;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowState;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ThrowMechanicEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private final ThrowState throwState;
    private final Player player;
    private final ThrowMechanic mechanic;

    public ThrowMechanicEvent(ThrowState state, Player player, ThrowMechanic mechanic) {
        this.throwState = state;
        this.player = player;
        this.mechanic = mechanic;
    }

    public ThrowMechanic getMechanic() {
        return this.mechanic;
    }

    public ThrowState getThrowState() {
        return this.throwState;
    }

    public Player getPlayer() {
        return this.player;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        this.cancelled = b;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

}


