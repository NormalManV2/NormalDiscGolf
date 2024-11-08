package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.listener.GoalScoreListener;
import normalmanv2.normalDiscGolf.impl.listener.PlayerJoinListener;
import normalmanv2.normalDiscGolf.test.BackHandTest;
import normalmanv2.normalDiscGolf.test.ForehandTest;
import normalmanv2.normalDiscGolf.test.TestFFARound;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class NormalDiscGolf extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("testBackHand").setExecutor(new BackHandTest(NDGManager.getInstance()));
        getCommand("testForeHand").setExecutor(new ForehandTest(NDGManager.getInstance()));
        getCommand("startRound").setExecutor(new TestFFARound(NDGManager.getInstance(), this));
        Bukkit.getPluginManager().registerEvents(new GoalScoreListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
    }

    @Override
    public void onDisable() {
    }
}
