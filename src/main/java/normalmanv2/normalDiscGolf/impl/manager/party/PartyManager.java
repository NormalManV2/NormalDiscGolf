package normalmanv2.normalDiscGolf.impl.manager.party;

import normalmanv2.normalDiscGolf.api.team.Team;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PartyManager {

    private final Set<Team> parties;

    public PartyManager() {
        this.parties = new HashSet<>();
    }

    public void addParty(Team team) {
        this.parties.add(team);
    }

    public void removeParty(Team team) {
        this.parties.remove(team);
    }

    public Set<Team> getParties() {
        return this.parties;
    }

    public void addPlayerToParty(Team team, UUID playerId) {
        team.addPlayer(playerId);
    }

    public void removePlayerFromParty(Team team, UUID playerId) {
        team.removePlayer(playerId);
    }

    public Team getPartyById(UUID id) {
        for (Team team : this.parties) {
            if (team.contains(id)) return team;
        }
        return null;
    }

}
