package normalmanv2.normalDiscGolf.impl.manager.party;

import normalmanv2.normalDiscGolf.api.team.Team;

import java.util.Collections;
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
        return Collections.unmodifiableSet(this.parties);
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

    public boolean isPartyOwner(UUID playerId) {
        Team party = this.getPartyById(playerId);
        return party != null && party.getOwner().equals(playerId);
    }

    public void leaveParty(UUID playerId) {
        Team party = this.getPartyById(playerId);
        if (party == null) {
            return;
        }

        if (party.getOwner().equals(playerId)) {
            this.removeParty(party);
            return;
        }

        party.removePlayer(playerId);
    }

}
