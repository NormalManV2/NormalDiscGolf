package normalmanv2.normalDiscGolf.api;

import java.util.Set;
import java.util.UUID;

public interface Team {

    void addPlayer(UUID player);
    void removePlayer(UUID player);
    Set<UUID> getPlayers();
    UUID getOwner();
}
