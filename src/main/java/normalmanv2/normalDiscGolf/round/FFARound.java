package normalmanv2.normalDiscGolf.round;

import normalmanv2.normalDiscGolf.attribute.PlayerAttribute;
import normalmanv2.normalDiscGolf.disc.Disc;
import normalmanv2.normalDiscGolf.technique.ThrowTechnique;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class FFARound implements GameRound {

    private final List<UUID> players;
    private boolean roundOver;
    private BukkitTask task;
    private final JavaPlugin plugin;

    public FFARound(JavaPlugin plugin) {
        this.players = new ArrayList<>();
        this.roundOver = false;
        this.plugin = plugin;
    }

    @Override
    public void startRound() {
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

        }, 0, 20);
    }

    @Override
    public void endRound() {
        this.task.cancel();
        this.task = null;
        this.roundOver = true;
    }

    @Override
    public void addPlayer(UUID player) {
        this.players.add(player);
    }

    @Override
    public void removePlayer(UUID player) {
        if (!this.players.contains(player)) {

            return;
        }
        this.players.remove(player);
    }

    @Override
    public void handleThrow(UUID playerId, ThrowTechnique technique, Disc disc) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        PlayerAttribute attribute = getAttributeByPlayer(player);


        disc.handleThrow(player, attribute, technique);
    }

    @Override
    public boolean isRoundOver() {
        return this.roundOver;
    }

    @Override
    public List<UUID> getPlayers() {
        return Collections.unmodifiableList(this.players);
    }
}