package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.common.mechanic.ThrowState;
import normalmanv2.normalDiscGolf.impl.event.DiscThrowEvent;
import normalmanv2.normalDiscGolf.impl.event.ThrowMechanicEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ThrowMechanicListener implements Listener {

    private final Map<ThrowState, UUID> throwState;
    private ThrowMechanicEvent throwMechanicEvent;

    public ThrowMechanicListener() {
        this.throwState = new HashMap<>();
    }

    @EventHandler
    public void onThrowMechanic(ThrowMechanicEvent event) {
        this.throwState.put(event.getThrowState(), event.getPlayer().getUniqueId());
        this.throwMechanicEvent = event;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {

        if (this.throwState.containsValue(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            ThrowMechanicImpl tm = (ThrowMechanicImpl) this.throwMechanicEvent.getMechanic();

            Bukkit.getPluginManager().callEvent(new DiscThrowEvent(tm));
            this.clearEntries();
            tm.getBossBar().removeAll();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (this.throwState.containsValue(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    private void clearEntries() {
        this.throwState.clear();
    }

}
