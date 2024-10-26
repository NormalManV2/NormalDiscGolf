package normalmanv2.normalDiscGolf.impl.team;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Team implements ITeam {

    private final UUID ownerId;
    private final Set<UUID> teamMembers;

    public Team(UUID ownerId) {
        this.ownerId = ownerId;
        this.teamMembers = new HashSet<>();
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
    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(this.teamMembers);
    }

    @Override
    public UUID getOwner() {
        return this.ownerId;
    }
}
