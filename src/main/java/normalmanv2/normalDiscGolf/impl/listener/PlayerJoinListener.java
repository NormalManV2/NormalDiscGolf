package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinListener implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        NDGManager manager = NDGManager.getInstance();
        manager.getPlayerDataManager().getOrCreatePlayerData(player.getUniqueId());
        manager.getPlayerSessionManager().restorePlayer(player);
        //NDGManager.getInstance().getPacked().send(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        NDGManager manager = NDGManager.getInstance();
        manager.getRoundQueueManager().removePlayerFromQueue(player.getUniqueId());
        manager.getPlayerSessionManager().restorePlayer(player);
        manager.getPlayerDataManager().savePlayerData(player.getUniqueId());
    }
}
