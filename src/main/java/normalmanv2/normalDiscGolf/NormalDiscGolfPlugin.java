package normalmanv2.normalDiscGolf;

import normalmanv2.normalDiscGolf.impl.NDGManager;
import normalmanv2.normalDiscGolf.impl.listener.DiscThrowListener;
import normalmanv2.normalDiscGolf.impl.listener.GoalScoreListener;
import normalmanv2.normalDiscGolf.impl.listener.GuiListener;
import normalmanv2.normalDiscGolf.impl.listener.PlayerJoinListener;
import normalmanv2.normalDiscGolf.impl.listener.PlayerRoundQueueListener;
import normalmanv2.normalDiscGolf.impl.listener.ThrowMechanicListener;
import normalmanv2.normalDiscGolf.test.RoundQueueTest;
import normalmanv2.normalDiscGolf.test.TestThrowMechanic;
import normalmanv2.normalDiscGolf.test.WFCTest;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class NormalDiscGolfPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        this.registerCommands();
        this.registerListeners();
    /*
        try {
            PackedIntegration integration = new PackedIntegration(this);
            integration.savePack();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
        */
    }

    private void registerCommands() {
        getCommand("wfctest").setExecutor(new WFCTest(this));
        getCommand("roundQueueTest").setExecutor(new RoundQueueTest());

        this.registerAbstractCommands();
    }

    private void registerAbstractCommands() {
        new TestThrowMechanic(this);
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new GoalScoreListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerRoundQueueListener(), this);
        Bukkit.getPluginManager().registerEvents(new GuiListener(NDGManager.getInstance().getGuiManager()), this);
        Bukkit.getPluginManager().registerEvents(new DiscThrowListener(), this);
        Bukkit.getPluginManager().registerEvents(new ThrowMechanicListener(), this);
    }

    @Override
    public void onDisable() {
    }
}
