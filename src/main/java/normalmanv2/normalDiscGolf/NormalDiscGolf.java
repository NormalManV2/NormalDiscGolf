package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.player.PlayerData;
import normalmanv2.normalDiscGolf.player.PlayerSkills;
import normalmanv2.normalDiscGolf.disc.Driver;
import normalmanv2.normalDiscGolf.player.PlayerDataManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class NormalDiscGolf extends JavaPlugin implements Listener {

    private final PlayerDataManager playerDataManager;

    public NormalDiscGolf() {
        this.playerDataManager = new PlayerDataManager();
    }

    @Override
    public void onEnable() {
        // Plugin startup logic
        Driver testDriver = new Driver(9, 5, -1, 2, this);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("testDriver").setExecutor(new DriverTest(testDriver, playerDataManager));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = new PlayerData();
        playerDataManager.registerPlayerData(player.getUniqueId(), playerData);

    }

    public PlayerDataManager getPlayerDataManager() {
        return this.playerDataManager;
    }


    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
