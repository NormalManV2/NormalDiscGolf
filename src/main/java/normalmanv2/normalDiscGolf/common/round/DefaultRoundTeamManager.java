package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.round.settings.RoundSettings;
import normalmanv2.normalDiscGolf.api.round.manager.RoundTeamManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTeamManager;
import normalmanv2.normalDiscGolf.api.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.*;

public class DefaultRoundTeamManager implements DelegateRoundTeamManager {
    private final Set<Team> teams = new HashSet<>();
    private final RoundSettings settings;

    public DefaultRoundTeamManager(RoundSettings settings) {
        this.settings = settings;
    }

    @Override
    public RoundTeamManager getRoundTeamManager() {
        return this;
    }

    @Override
    public boolean addTeam(Team team) {
        if (this.isFull()) {
            for (UUID playerId : team.getTeamMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    continue;
                }
                player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4This round is full!"));
            }
            return false;
        }

        return this.getRoundTeamManager().addTeam(team);
    }

    @Override
    public void removeTeam(Team team) {
        this.teams.remove(team);
    }

    @Override
    public boolean isFull() {
        return this.teams.size() >= this.settings.maxTeams();
    }

    @Override
    public List<Team> getTeams() {
        return this.teams.stream().toList();
    }

    @Override
    public boolean containsTeam(Team team) {
        return DelegateRoundTeamManager.super.containsTeam(team);
    }

    @Override
    public boolean containsPlayer(UUID playerId) {
        return DelegateRoundTeamManager.super.containsPlayer(playerId);
    }

    public void tick() {
        Iterator<Team> it = this.teams.iterator();
        while (it.hasNext()) {
            Team team = it.next();
            Iterator<UUID> members = team.getTeamMembers().iterator();

            while (members.hasNext()) {
                UUID playerId = members.next();
                Player player = Bukkit.getPlayer(playerId);
                if (player == null) {
                    members.remove();
                }
            }

            if (team.getTeamMembers().isEmpty()) {
                it.remove();
            }
        }
    }
}
