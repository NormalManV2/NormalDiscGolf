package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.api.API;
import normalmanv2.normalDiscGolf.player.PlayerData;
import normalmanv2.normalDiscGolf.disc.Driver;
import normalmanv2.normalDiscGolf.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.round.FFARound;
import normalmanv2.normalDiscGolf.round.RoundHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class NormalDiscGolf extends JavaPlugin implements Listener {

    private static final PlayerDataManager playerDataManager = new PlayerDataManager();
    private static final RoundHandler roundHandler = new RoundHandler();

    @Override
    public void onEnable() {
        // Plugin startup logic
        Driver testDriver = new Driver(9, 5, -1, 2, this);
        Driver testDriver1 = new Driver(9, 5, -3, 1, this);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("testDriver").setExecutor(new BackHandTest(testDriver, playerDataManager));
        getCommand("testDriver1").setExecutor(new ForehandTest(testDriver1, playerDataManager));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = new PlayerData();
        playerDataManager.registerPlayerData(player.getUniqueId(), playerData);
    }

    public void startFFARound() {
        FFARound ffaRound = new FFARound(this);
    }

    public static PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public static RoundHandler getRoundHandler() {
        return roundHandler;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
