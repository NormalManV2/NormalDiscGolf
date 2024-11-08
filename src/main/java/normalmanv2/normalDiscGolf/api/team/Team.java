package normalmanv2.normalDiscGolf.api.team;

import java.util.Set;
import java.util.UUID;

public interface Team {

    void addPlayer(UUID playerId);
    void removePlayer(UUID playerId);
    Set<UUID> getTeamMembers();
    UUID getOwner();

}
