package normalmanv2.normalDiscGolf.common.round;

import normalmanv2.normalDiscGolf.api.round.GameRound;
import normalmanv2.normalDiscGolf.api.round.Wirable;
import normalmanv2.normalDiscGolf.api.round.manager.RoundTeamManager;
import normalmanv2.normalDiscGolf.api.round.delegate.manager.DelegateRoundTeamManager;
import normalmanv2.normalDiscGolf.api.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DefaultRoundTeamManager implements DelegateRoundTeamManager, Wirable {
    private final Set<Team> teams;
    private GameRound round;
    private boolean isWired = false;

    public DefaultRoundTeamManager() {
        this.teams = new HashSet<>();
    }

    @Override
    public RoundTeamManager getRoundTeamManager() {
        return this;
    }

    @Override
    public void wire(@NotNull GameRound round, @Nullable Plugin plugin) {
        if (this.isWired) {
            throw new IllegalStateException("This team manager is already wired!");
        }

        this.round = round;
        this.isWired = true;
    }

    @Override
    public void unwire() {
        if (!this.isWired) {
            throw new IllegalStateException("This team manager is not wired!");
        }
        this.round = null;
        this.isWired = false;
    }

    @Override
    public boolean isWired() {
        return this.isWired;
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
        this.teams.add(team);
        return this.getRoundTeamManager().addTeam(team);
    }

    @Override
    public void removeTeam(Team team) {
        this.teams.remove(team);
    }

    @Override
    public boolean isFull() {
        return this.teams.size() >= this.round.getSettings().maxTeams();
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

    @Override
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

    public void dispose() {
        this.teams.clear();
    }
}
