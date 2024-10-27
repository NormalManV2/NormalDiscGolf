package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.api.NDGApi;
import normalmanv2.normalDiscGolf.impl.listener.GoalScoreListener;
import normalmanv2.normalDiscGolf.impl.listener.PlayerJoinListener;
import normalmanv2.normalDiscGolf.test.BackHandTest;
import normalmanv2.normalDiscGolf.test.ForehandTest;
import normalmanv2.normalDiscGolf.test.TestFFARound;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public final class NormalDiscGolf extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("testBackHand").setExecutor(new BackHandTest(NDGApi.getInstance()));
        getCommand("testForeHand").setExecutor(new ForehandTest(NDGApi.getInstance()));
        getCommand("startRound").setExecutor(new TestFFARound(NDGApi.getInstance(), this));
        Bukkit.getPluginManager().registerEvents(new GoalScoreListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
    }

    @Override
    public void onDisable() {
    }
}
