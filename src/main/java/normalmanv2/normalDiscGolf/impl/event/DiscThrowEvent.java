package normalmanv2.normalDiscGolf.impl.event;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.course.CourseImpl;
import normalmanv2.normalDiscGolf.common.disc.DiscImpl;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DiscThrowEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ThrowMechanicImpl throwMechanic;
    private final Player player;
    private boolean cancelled;

    public DiscThrowEvent(ThrowMechanicImpl throwMechanic) {
        this.throwMechanic = throwMechanic;
        this.player = Bukkit.getPlayer(this.throwMechanic.getPlayerId());
    }

    public DiscImpl getThrownDisc() {
        return throwMechanic.getDisc();
    }

    public double getPower() {
        return throwMechanic.getPower().get();
    }

    public Player getPlayer() {
        return Bukkit.getPlayer(throwMechanic.getPlayerId());
    }

    public GameRound getRound() {
        return throwMechanic.getRound();
    }

    public String getTechnique() {
        return throwMechanic.getThrowTechnique();
    }

    public ThrowMechanicImpl getThrowMechanic() {
        return this.throwMechanic;
    }

    public boolean isTurn() {
        for (Team foundTeam : this.throwMechanic.getRound().getTeams()) {
            if (!foundTeam.contains(this.player.getUniqueId())) continue;
            if (this.throwMechanic.getRound().isTurn(foundTeam)) return true;
        }
        return false;
    }

    public boolean isRound(GameRound comparedRound) {
        return this.throwMechanic.getRound() == comparedRound;
    }

    public boolean isTechnique(String technique) {
        return this.throwMechanic.getThrowTechnique().equals(technique);
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean b) {
        this.cancelled = b;
    }
}
