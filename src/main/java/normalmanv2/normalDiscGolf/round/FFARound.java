package normalmanv2.normalDiscGolf.round;

import normalmanv2.normalDiscGolf.NormalDiscGolf;
import normalmanv2.normalDiscGolf.player.PlayerData;
import normalmanv2.normalDiscGolf.player.PlayerSkills;
import normalmanv2.normalDiscGolf.disc.Disc;
import normalmanv2.normalDiscGolf.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.player.score.PDGARating;
import normalmanv2.normalDiscGolf.player.score.PlayerScoreCard;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class FFARound implements GameRound {

    private final List<UUID> players;
    private final ConcurrentMap<UUID, PlayerScoreCard> scoreCards;
    private boolean roundOver;
    private BukkitTask task;
    private final JavaPlugin plugin;
    private final PlayerDataManager playerDataManager;

    public FFARound(JavaPlugin plugin) {
        this.players = new ArrayList<>();
        this.scoreCards = new ConcurrentHashMap<>();
        this.roundOver = false;
        this.plugin = plugin;
        this.playerDataManager = NormalDiscGolf.getPlayerDataManager();
    }

    @Override
    public void startRound() {

        for (UUID uuid : players) {
            this.scoreCards.put(uuid, new PlayerScoreCard());
        }

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

        }, 0, 20);
    }

    @Override
    public void endRound() {
        this.handleRoundEnd();
    }

    @Override
    public void cancelRound() {
        this.dispose();
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
    public void handleStroke(UUID playerId, String technique, Disc disc) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        PlayerData playerData = playerDataManager.getDataByPlayer(playerId);
        PlayerSkills skills = playerData.getSkills();

        disc.handleThrow(player, skills, technique);
    }

    @Override
    public boolean isRoundOver() {
        return this.roundOver;
    }

    @Override
    public List<UUID> getPlayers() {
        return Collections.unmodifiableList(this.players);
    }

    private void handleRoundEnd() {
        this.roundOver = true;
        for (UUID uuid : this.players) {
            PlayerScoreCard scoreCard = this.scoreCards.get(uuid);
            if (scoreCard == null) {
                return;
            }
            PlayerData playerData = playerDataManager.getDataByPlayer(uuid);
            PDGARating rating = playerData.getRating();
            rating.handleRoundEnd(scoreCard.getTotalScore());
        }
        this.dispose();
    }

    public BukkitTask getRoundTask() {
        return this.task;
    }

    private void dispose(){
        this.task.cancel();
        this.task = null;
        this.scoreCards.clear();
        this.players.clear();
    }

}