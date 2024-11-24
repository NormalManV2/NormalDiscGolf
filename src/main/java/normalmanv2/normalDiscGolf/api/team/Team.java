package normalmanv2.normalDiscGolf.api.team;

import normalmanv2.normalDiscGolf.api.component.Component;

import java.util.Set;
import java.util.UUID;

public interface Team {

    void addPlayer(UUID playerId);
    void removePlayer(UUID playerId);
    Set<UUID> getTeamMembers();
    UUID getOwner();
    Set<Component> getTeamComponents();
    void addComponent(Component component);
    void removeComponent(Component component);
    Component getComponent(String componentId);

}
