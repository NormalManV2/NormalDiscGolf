package normalmanv2.normalDiscGolf.impl.team;

import normalmanv2.normalDiscGolf.api.team.Team;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TeamImpl implements Team {

    private final UUID ownerId;
    private final Set<UUID> teamMembers;

    public TeamImpl(UUID ownerId) {
        this.ownerId = ownerId;
        this.teamMembers = new HashSet<>();
        this.teamMembers.add(ownerId);
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
}
