package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.api.NDGApi;
import normalmanv2.normalDiscGolf.impl.disc.MidRange;
import normalmanv2.normalDiscGolf.impl.disc.Putter;
import normalmanv2.normalDiscGolf.impl.listener.GoalScoreListener;
import normalmanv2.normalDiscGolf.impl.player.PlayerData;
import normalmanv2.normalDiscGolf.impl.disc.Driver;
import normalmanv2.normalDiscGolf.impl.player.PlayerDataManager;
import normalmanv2.normalDiscGolf.impl.registry.DiscRegistry;
import normalmanv2.normalDiscGolf.impl.round.RoundHandler;
import normalmanv2.normalDiscGolf.impl.registry.ThrowTechniqueRegistry;
import normalmanv2.normalDiscGolf.test.BackHandTest;
import normalmanv2.normalDiscGolf.test.ForehandTest;
import normalmanv2.normalDiscGolf.test.TestFFARound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class NormalDiscGolf extends JavaPlugin implements Listener {

    private static final PlayerDataManager playerDataManager = new PlayerDataManager();
    private static final RoundHandler roundHandler = new RoundHandler();
    private static final ThrowTechniqueRegistry throwTechniqueRegistry = new ThrowTechniqueRegistry();
    private static final DiscRegistry discRegistry = new DiscRegistry();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        this.registerDefaultDiscs();
        getCommand("testBackHand").setExecutor(new BackHandTest(NDGApi.getInstance()));
        getCommand("testForeHand").setExecutor(new ForehandTest(NDGApi.getInstance()));
        getCommand("startRound").setExecutor(new TestFFARound(NDGApi.getInstance(), this));
        Bukkit.getPluginManager().registerEvents(new GoalScoreListener(), this);
    }

    private void registerDefaultDiscs() {
        discRegistry.registerDisc("TestDriver", new Driver(9, 5, -1, 2, "&6TestDriver", this));
        discRegistry.registerDisc("TestDriver1", new Driver(9, 5, -3, 1, "&aTestDriver1", this));
        discRegistry.registerDisc("Midrange", new MidRange(5, 6, -1, 1, "&7Midrange", this));
        discRegistry.registerDisc("Midrange1", new MidRange(5, 6, -3, 1, "&cMidrange1", this));
        discRegistry.registerDisc("Putter", new Putter(2, 4, -1, 1, "&4Putter", this));
        discRegistry.registerDisc("Putter1", new Putter(2, 4, 0, 2, "&3Putter1", this));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = new PlayerData();
        playerDataManager.registerPlayerData(player.getUniqueId(), playerData);
    }

    public static PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public static RoundHandler getRoundHandler() {
        return roundHandler;
    }

    public static ThrowTechniqueRegistry getThrowTechniqueRegistry() {
        return throwTechniqueRegistry;
    }

    public static DiscRegistry getDiscRegistry() {
        return discRegistry;
    }

    @Override
    public void onDisable() {
    }
}
