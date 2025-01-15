package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.api.team.Team;
import normalmanv2.normalDiscGolf.impl.event.DiscThrowEvent;
import normalmanv2.normalDiscGolf.impl.round.FFARound;
import normalmanv2.normalDiscGolf.impl.round.RoundImpl;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class DiscThrowListener implements Listener {

    @EventHandler
    public void onThrow(DiscThrowEvent event) {

        RoundImpl roundImpl = (RoundImpl) event.getRound();

        if (!event.isTurn()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.translateAlternateColorCodes('&', "&4It is not your turn!"));
        }

        roundImpl.handleStroke(event.getPlayer().getUniqueId(), event.getTechnique(), event.getThrownDisc());

        if (roundImpl instanceof FFARound) {
            roundImpl.nextTurn();
        }
    }
}
