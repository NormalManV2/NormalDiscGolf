package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.impl.event.DiscThrowEvent;
import normalmanv2.normalDiscGolf.impl.manager.task.TaskManager;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;

public class DiscThrowListener implements Listener {

    @EventHandler
    public void onThrow(DiscThrowEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            event.setCancelled(true);
            return;
        }

        RoundImpl roundImpl = (RoundImpl) event.getRound();

        if (!event.isTurn()) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&4It is not your turn!"));
            return;
        }

        for (BukkitTask foundTask : new ArrayList<>(TaskManager.getActiveTasks().keySet())) {
            if (TaskManager.getActiveTasks().get(foundTask).equals(player.getUniqueId())) {
                TaskManager.unregisterTask(foundTask);
            }
        }

        roundImpl.handleStroke(event.getThrowMechanic());
    }
}
