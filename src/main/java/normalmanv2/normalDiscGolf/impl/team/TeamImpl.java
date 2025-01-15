package normalmanv2.normalDiscGolf.impl.team;

import normalmanv2.normalDiscGolf.api.component.Component;
import normalmanv2.normalDiscGolf.api.team.Team;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TeamImpl implements Team {

    private final UUID ownerId;
    private final Set<UUID> teamMembers;
    private final Set<Component<?>> teamComponents;
    private final int maximumPlayers;

    public TeamImpl(UUID ownerId, int maximumPlayers) {
        this.ownerId = ownerId;
        this.teamMembers = new HashSet<>();
        this.teamComponents = new HashSet<>();
        this.maximumPlayers = maximumPlayers;
        this.teamMembers.add(ownerId);
    }

    @Override
    public boolean contains(UUID playerId) {
        return this.teamMembers.contains(playerId);
    }

    @Override
    public int getMaximumPlayers() {
        return this.maximumPlayers;
    }

    @Override
    public boolean isFull() {
        return this.teamMembers.size() >= this.maximumPlayers;
    }

    @Override
    public void addPlayer(UUID player) {
        this.teamMembers.add(player);
    }

    @Override
    public void removePlayer(UUID player) {
        this.teamMembers.remove(player);
    }

    @Override
    public Set<UUID> getTeamMembers() {
        return Collections.unmodifiableSet(this.teamMembers);
    }

    @Override
    public UUID getOwner() {
        return this.ownerId;
    }

    @Override
    public Set<Component<?>> getTeamComponents() {
        return Collections.unmodifiableSet(this.teamComponents);
    }

    @Override
    public void addComponent(Component<?> component) {
        this.teamComponents.add(component);
    }

    @Override
    public void removeComponent(Component<?> component) {
        this.teamComponents.remove(component);
    }

    @Override
    public Component<?> getComponent(String componentId) {

        for (Component<?> component : this.teamComponents) {
            if (!component.getId().equalsIgnoreCase(componentId)) {
                continue;
            }
            return component;
        }

        return null;
    }
}
