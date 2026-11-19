package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.impl.event.DiscThrowEvent;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

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

        try {
            if (roundImpl.handleStroke(event.getThrowMechanic())) {
                player.sendMessage(ChatColor.GREEN + "Disc released.");
            } else {
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "The throw could not be released.");
            }
        } catch (RuntimeException exception) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Throw failed: " + exception.getMessage());
            exception.printStackTrace();
        }
    }
}
