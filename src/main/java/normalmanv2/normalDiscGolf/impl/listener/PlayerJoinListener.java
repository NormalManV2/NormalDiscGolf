package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = new PlayerData();
        NDGManager.getInstance().getPlayerDataManager().registerPlayerData(player.getUniqueId(), playerData);
        NDGManager.getInstance().getPackedIntegration().send(player);
    }
}
