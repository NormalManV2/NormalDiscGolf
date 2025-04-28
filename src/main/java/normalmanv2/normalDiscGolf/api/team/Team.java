package normalmanv2.normalDiscGolf.api.team;

import normalmanv2.normalDiscGolf.api.component.Component;
import org.normal.api.context.Context;
import org.normal.api.context.ContextType;

import java.util.Set;
import java.util.UUID;

public interface Team {

    int getMaximumPlayers();

    boolean isFull();

    void addPlayer(UUID playerId);

    void removePlayer(UUID playerId);

    Set<UUID> getTeamMembers();

    UUID getOwner();

    Set<Component<?>> getTeamComponents();

    boolean contains(UUID playerId);

    void addComponent(Component<?> component);

    void removeComponent(Component<?> component);

    Component<?> getComponent(String componentId);

    Context<? extends ContextType> setTurnIndex(int turn);
    int getTurnIndexContext();
}
