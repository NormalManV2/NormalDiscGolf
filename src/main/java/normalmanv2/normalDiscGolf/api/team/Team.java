package normalmanv2.normalDiscGolf.api.team;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Team {

    private final UUID ownerId;
    private final Set<UUID> teamMembers;

    public Team(UUID ownerId) {
        this.ownerId = ownerId;
        this.teamMembers = new HashSet<>();
        this.teamMembers.add(ownerId);
    }

    public void addPlayer(UUID player) {
        this.teamMembers.add(player);
    }

    public void removePlayer(UUID player) {
        this.teamMembers.remove(player);
    }

    public Set<UUID> getPlayers() {
        return Collections.unmodifiableSet(this.teamMembers);
    }

    public UUID getOwner() {
        return this.ownerId;
    }
}
