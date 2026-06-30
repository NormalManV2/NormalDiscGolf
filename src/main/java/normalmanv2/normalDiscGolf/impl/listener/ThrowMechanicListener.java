package normalmanv2.normalDiscGolf.impl.listener;

import normalmanv2.normalDiscGolf.common.mechanic.ThrowMechanicImpl;
import normalmanv2.normalDiscGolf.impl.event.DiscThrowEvent;
import normalmanv2.normalDiscGolf.impl.event.ThrowMechanicEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ThrowMechanicListener implements Listener {

    private final Map<UUID, ThrowMechanicImpl> activeMechanics;

    public ThrowMechanicListener() {
        this.activeMechanics = new HashMap<>();
    }

    @EventHandler
    public void onThrowMechanic(ThrowMechanicEvent event) {
        ThrowMechanicImpl mechanic = (ThrowMechanicImpl) event.getMechanic();
        ThrowMechanicImpl previousMechanic = this.activeMechanics.put(event.getPlayer().getUniqueId(), mechanic);

        if (previousMechanic != null && previousMechanic.getBossBar() != null) {
            previousMechanic.getBossBar().removeAll();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        ThrowMechanicImpl mechanic = this.activeMechanics.remove(event.getPlayer().getUniqueId());
        if (mechanic == null) {
            return;
        }

        event.setCancelled(true);
        int lockedPower = (int) Math.round(mechanic.getPower().get() * 100);
        event.getPlayer().sendMessage(ChatColor.GRAY + "Power locked: " + lockedPower + "%");

        Bukkit.getPluginManager().callEvent(new DiscThrowEvent(mechanic));

        if (mechanic.getBossBar() != null) {
            mechanic.getBossBar().removeAll();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (this.activeMechanics.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
