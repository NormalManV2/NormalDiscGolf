package normalmanv2.normalDiscGolf.impl.team;

import java.util.Set;
import java.util.UUID;

public interface ITeam {

    void addPlayer(UUID player);
    void removePlayer(UUID player);
    Set<UUID> getPlayers();
    UUID getOwner();
}
