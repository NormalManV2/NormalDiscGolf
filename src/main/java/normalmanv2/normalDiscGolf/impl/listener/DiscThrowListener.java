package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.impl.event.DiscThrowEvent;
import normalmanv2.normalDiscGolf.impl.manager.task.TaskManager;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class DiscThrowListener implements Listener {

    @EventHandler
    public void onThrow(DiscThrowEvent event) {

        RoundImpl roundImpl = (RoundImpl) event.getRound();

        if (!event.isTurn()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', "&4It is not your turn!"));
        }

        for (BukkitTask foundTask : TaskManager.getActiveTasks().keySet()) {
            if (TaskManager.getActiveTasks().get(foundTask).equals(event.getPlayer().getUniqueId())) {
                TaskManager.unregisterTask(foundTask);
            }
        }

        roundImpl.handleStroke(event.getThrowMechanic());
        roundImpl.nextTurn();

        if (roundImpl instanceof FFARound) {
            roundImpl.nextTurn();
        }
    }
}
